package com.example.wallet.webhook;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.deposit.DepositService;
import com.example.wallet.deposit.SettlementResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Payment-provider webhooks.
 *
 * <pre>
 *  verify HMAC on raw bytes  --fail-->  store REJECTED event, 401
 *  parse JSON
 *  one transaction:
 *     INSERT event ON CONFLICT (provider, provider_event_id) DO NOTHING
 *     already PROCESSED?            --> 200 "duplicate", nothing reprocessed
 *     apply to deposit (locks deposit row; posts ledger entries)
 *     mark event PROCESSED / FAILED
 *  unexpected error  --> rollback, store FAILED event (own tx), 500 so the provider retries
 * </pre>
 *
 * Every event is persisted, including forgeries, for forensics. Rejected events get a
 * synthetic id ("rejected:..."): a forged event must never occupy the id of a real one, or an
 * attacker could pre-empt a legitimate delivery and have it ignored as a "duplicate".
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome { PROCESSED, DUPLICATE, FAILED }

    /** Provider payloads may gain fields over time; tolerate unknown ones (unlike our own API). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(String id, String type, Data data) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(String providerReference, Long amount, String currency) {
        }
    }

    private final HmacSignatureVerifier verifier;
    private final WebhookProperties props;
    private final DepositService deposits;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate separateTx;
    private final ObjectMapper objectMapper;
    private final ObjectReader eventReader;
    private final Clock clock;
    private final MeterRegistry registry;

    public WebhookService(WebhookProperties props, DepositService deposits, JdbcTemplate jdbc,
                          PlatformTransactionManager txManager, ObjectMapper objectMapper, Clock clock,
                          MeterRegistry registry) {
        this.verifier = new HmacSignatureVerifier(Arrays.asList(props.secret(), props.previousSecret()),
                props.tolerance(), clock);
        this.props = props;
        this.deposits = deposits;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.separateTx = new TransactionTemplate(txManager);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
        this.eventReader = objectMapper.readerFor(Event.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.clock = clock;
        this.registry = registry;
    }

    public Outcome handle(byte[] rawBody, String timestamp, String signature) {
        HmacSignatureVerifier.Result verification = verifier.verify(timestamp, signature, rawBody);
        if (verification != HmacSignatureVerifier.Result.VALID) {
            recordRejected(rawBody, signature, verification.name());
            count("rejected");
            log.warn("Webhook rejected: {}", verification);
            throw new ApiException(ErrorCode.INVALID_SIGNATURE, "Webhook signature verification failed");
        }

        Event event = parse(rawBody);
        if (event == null || event.id() == null || event.type() == null) {
            recordRejected(rawBody, signature, "MALFORMED_PAYLOAD");
            count("rejected");
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed webhook payload");
        }

        try {
            Outcome outcome = tx.execute(s -> process(event, rawBody, signature));
            count(outcome.name().toLowerCase());
            return outcome;
        } catch (RuntimeException e) {
            log.error("Webhook {} processing failed", event.id(), e);
            separateTx.executeWithoutResult(s -> upsertFailed(event, rawBody, signature, e.getClass().getSimpleName()));
            count("error");
            throw e;
        }
    }

    private Outcome process(Event event, byte[] rawBody, String signature) {
        String payload = jsonOrWrapped(rawBody);
        List<UUID> inserted = jdbc.query("""
                INSERT INTO webhook_events (id, provider, provider_event_id, event_type, payload, signature, status, received_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 'RECEIVED', ?)
                ON CONFLICT (provider, provider_event_id) DO NOTHING
                RETURNING id
                """, (rs, n) -> rs.getObject("id", UUID.class),
                UuidV7.generate(clock), props.provider(), event.id(), event.type(), payload, signature, now());

        UUID eventRowId;
        if (inserted.isEmpty()) {
            Map<String, Object> existing = jdbc.queryForMap(
                    "SELECT id, status FROM webhook_events WHERE provider = ? AND provider_event_id = ? FOR UPDATE",
                    props.provider(), event.id());
            if ("PROCESSED".equals(existing.get("status"))) {
                return Outcome.DUPLICATE;
            }
            eventRowId = (UUID) existing.get("id"); // an earlier attempt failed: process again
        } else {
            eventRowId = inserted.get(0);
        }

        SettlementResult result = apply(event);
        String error = result instanceof SettlementResult.Rejected rejected ? rejected.reason() : null;
        jdbc.update("UPDATE webhook_events SET status = ?, error = ?, processed_at = ? WHERE id = ?",
                error == null ? "PROCESSED" : "FAILED", error, now(), eventRowId);
        // A rejected-but-authentic event is acknowledged (200): retrying it would not help.
        return error == null ? Outcome.PROCESSED : Outcome.FAILED;
    }

    private SettlementResult apply(Event event) {
        Event.Data data = event.data();
        if (data == null || data.providerReference() == null) {
            return new SettlementResult.Rejected("Missing data.providerReference");
        }
        return switch (event.type()) {
            case "deposit.succeeded" -> {
                if (data.amount() == null || data.currency() == null) {
                    yield new SettlementResult.Rejected("Missing amount or currency");
                }
                yield deposits.settleSucceeded(data.providerReference(), data.amount(), data.currency());
            }
            case "deposit.failed" -> deposits.markFailed(data.providerReference());
            default -> new SettlementResult.Rejected("Unsupported event type " + event.type());
        };
    }

    private void recordRejected(byte[] rawBody, String signature, String reason) {
        Event claimed = parse(rawBody);
        String claimedType = claimed != null && claimed.type() != null ? claimed.type() : "unknown";
        separateTx.executeWithoutResult(s -> jdbc.update("""
                INSERT INTO webhook_events (id, provider, provider_event_id, event_type, payload, signature,
                                            status, error, received_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 'REJECTED', ?, ?)
                """, UuidV7.generate(clock), props.provider(), "rejected:" + UUID.randomUUID(), claimedType,
                jsonOrWrapped(rawBody), truncate(signature, 200), reason, now()));
    }

    private void upsertFailed(Event event, byte[] rawBody, String signature, String error) {
        jdbc.update("""
                INSERT INTO webhook_events (id, provider, provider_event_id, event_type, payload, signature,
                                            status, error, received_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 'FAILED', ?, ?)
                ON CONFLICT (provider, provider_event_id)
                DO UPDATE SET status = 'FAILED', error = EXCLUDED.error
                WHERE webhook_events.status <> 'PROCESSED'
                """, UuidV7.generate(clock), props.provider(), event.id(), event.type(), jsonOrWrapped(rawBody),
                signature, error, now());
    }

    private Event parse(byte[] rawBody) {
        try {
            return eventReader.readValue(rawBody);
        } catch (Exception e) {
            return null;
        }
    }

    /** payload is JSONB: store invalid JSON wrapped, so even garbage is kept for forensics. */
    private String jsonOrWrapped(byte[] rawBody) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (node != null && !node.isMissingNode()) {
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception ignored) {
            // fall through
        }
        String text = new String(rawBody == null ? new byte[0] : rawBody, StandardCharsets.UTF_8);
        try {
            return objectMapper.writeValueAsString(Map.of("unparseable", true, "raw", truncate(text, 2000)));
        } catch (Exception e) {
            return "{\"unparseable\": true}";
        }
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private void count(String outcome) {
        Counter.builder("wallet.webhooks").tag("outcome", outcome)
                .description("Payment-provider webhooks by outcome").register(registry).increment();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
