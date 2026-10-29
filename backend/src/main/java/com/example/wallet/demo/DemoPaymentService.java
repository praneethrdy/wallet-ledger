package com.example.wallet.demo;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.deposit.DepositDtos.DepositResponse;
import com.example.wallet.deposit.DepositService;
import com.example.wallet.deposit.DepositStatus;
import com.example.wallet.webhook.HmacSignatureVerifier;
import com.example.wallet.webhook.WebhookProperties;
import com.example.wallet.webhook.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Plays the payment provider for a customer's own deposit, so a public demo can "add money"
 * without a real payment provider. It does exactly what the provider would: builds a deposit
 * event, signs it with the webhook secret, and hands it to the normal webhook pipeline
 * (signature check, event de-duplication, idempotent settlement). Nothing about how money
 * reaches the ledger is special-cased.
 */
@Service
public class DemoPaymentService {

    public enum Outcome { SUCCESS, FAILURE }

    private final DemoPaymentProperties props;
    private final DepositService deposits;
    private final WebhookService webhooks;
    private final WebhookProperties webhookProperties;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DemoPaymentService(DemoPaymentProperties props, DepositService deposits, WebhookService webhooks,
                              WebhookProperties webhookProperties, AuditService audit, ObjectMapper objectMapper,
                              Clock clock) {
        this.props = props;
        this.deposits = deposits;
        this.webhooks = webhooks;
        this.webhookProperties = webhookProperties;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public DepositResponse complete(AuthUser caller, UUID depositId, Outcome outcome) {
        if (!props.enabled()) {
            throw ApiException.notFound("Endpoint"); // switched off: behave as if it didn't exist
        }
        DepositResponse deposit = deposits.get(caller, depositId); // 404 unless it's the caller's
        if (deposit.status() != DepositStatus.PENDING) {
            return deposit; // already settled: a repeat click changes nothing
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("providerReference", deposit.providerReference());
        data.put("amount", deposit.amount());
        data.put("currency", deposit.currency());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt_demo_" + UUID.randomUUID().toString().replace("-", ""));
        event.put("type", outcome == Outcome.SUCCESS ? "deposit.succeeded" : "deposit.failed");
        event.put("created", clock.instant().getEpochSecond());
        event.put("data", data);

        byte[] body = json(event);
        long timestamp = clock.instant().getEpochSecond();
        webhooks.handle(body, Long.toString(timestamp),
                HmacSignatureVerifier.sign(webhookProperties.secret(), timestamp, body));
        audit.record(caller.id(), "DEMO_PAYMENT_COMPLETED", "deposit", depositId, Map.of("outcome", outcome.name()),
                null);
        return deposits.get(caller, depositId);
    }

    private byte[] json(Object value) {
        try {
            return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
