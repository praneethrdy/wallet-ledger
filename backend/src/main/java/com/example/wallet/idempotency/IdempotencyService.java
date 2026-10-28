package com.example.wallet.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.common.error.ProblemDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Makes a mutating endpoint safe to retry: the same {@code Idempotency-Key} + same request
 * produces the same effect once, and the same response every time.
 *
 * <pre>
 *  1. reserve   (own tx, committed)  INSERT key ... ON CONFLICT DO NOTHING
 *               existing COMPLETED + same hash  -> replay stored response
 *               existing IN_PROGRESS            -> wait briefly, then replay or 409
 *               existing + different hash       -> 422 IDEMPOTENCY_KEY_REUSED
 *  2. execute   (business tx)  lock key row FOR UPDATE, check we still own it,
 *               run the operation, store the response + mark COMPLETED  -> commit together
 *  3. on 4xx    nothing was changed; store the error response for replay (own tx)
 *     on 5xx    delete the reservation so a retry can run again
 * </pre>
 *
 * <p>WHY the response is stored in the SAME transaction as the business change: if they were
 * separate, a crash between "transfer committed" and "key marked COMPLETED" would leave an
 * IN_PROGRESS key that a retry could take over, executing the transfer twice. Here either
 * both commit or neither does. See ADR-012 for why this is a service wrapper, not a filter.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    /** Visible ASCII, 1-255 chars: a UUID is the recommended choice. */
    private static final Pattern KEY_FORMAT = Pattern.compile("^[\\x21-\\x7E]{1,255}$");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate businessTx;
    private final TransactionTemplate separateTx;
    private final ObjectMapper objectMapper;
    private final RequestHasher hasher;
    private final IdempotencyProperties props;
    private final Clock clock;
    private final Counter replays;

    public IdempotencyService(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper objectMapper,
                              RequestHasher hasher, IdempotencyProperties props, Clock clock, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.businessTx = new TransactionTemplate(txManager);
        this.separateTx = new TransactionTemplate(txManager);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
        this.hasher = hasher;
        this.props = props;
        this.clock = clock;
        this.replays = Counter.builder("wallet.idempotency.replays")
                .description("Responses replayed for a repeated Idempotency-Key").register(registry);
    }

    /**
     * @param body      the parsed request body (hashed canonically), or null
     * @param operation receives the idempotency key's id (to link from the ledger transaction)
     */
    public <T> ResponseEntity<T> execute(AuthUser caller, String key, HttpServletRequest http, Object body,
                                         Function<UUID, ResponseEntity<T>> operation) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be 1-255 visible ASCII characters (a UUID is recommended)");
        }
        String method = http.getMethod();
        String path = http.getRequestURI();
        String hash = hasher.hash(method, path, body);

        Outcome outcome = reserveOrWait(caller.id(), key, method, path, hash);
        if (outcome instanceof Replay replay) {
            return replayed(replay);
        }
        Reserved reserved = (Reserved) outcome;

        try {
            return businessTx.execute(status -> {
                assertStillOwned(reserved);
                ResponseEntity<T> response = operation.apply(reserved.id());
                complete(reserved, response.getStatusCode(), toJson(response.getBody()), true);
                return response;
            });
        } catch (ReservationLostException e) {
            throw e;
        } catch (ApiException e) {
            if (e.code().status().is4xxClientError()) {
                // Business rule refused it; nothing changed. Replays must give the same answer.
                ProblemDetail problem = ProblemDetails.of(e.code(), e.getMessage(), path);
                separateTx.executeWithoutResult(s -> complete(reserved, e.code().status(), toJson(problem), false));
            } else {
                release(reserved);
            }
            throw e;
        } catch (RuntimeException | Error e) {
            release(reserved);
            throw e;
        }
    }

    // ---- reservation ---------------------------------------------------------------------

    private sealed interface Outcome permits Reserved, Replay, InFlight {
    }

    private record Reserved(UUID id, Instant lockedAt) implements Outcome {
    }

    private record Replay(int status, String bodyJson) implements Outcome {
    }

    private record InFlight() implements Outcome {
    }

    private record ExistingKey(UUID id, String hash, String status, Integer responseStatus, String responseBody,
                               Instant lockedAt, Instant expiresAt) {
    }

    private Outcome reserveOrWait(UUID userId, String key, String method, String path, String hash) {
        long deadline = System.nanoTime() + props.inFlightWait().toNanos();
        while (true) {
            Outcome outcome = separateTx.execute(s -> tryReserve(userId, key, method, path, hash));
            if (!(outcome instanceof InFlight)) {
                return outcome;
            }
            if (System.nanoTime() > deadline) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            }
            sleep(50);
        }
    }

    private Outcome tryReserve(UUID userId, String key, String method, String path, String hash) {
        for (int attempt = 0; attempt < 3; attempt++) {
            Instant now = now();
            List<Reserved> inserted = jdbc.query("""
                    INSERT INTO idempotency_keys (id, user_id, key, request_method, request_path, request_hash,
                                                  status, locked_at, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?)
                    ON CONFLICT (user_id, key) DO NOTHING
                    RETURNING id, locked_at
                    """, (rs, n) -> new Reserved(rs.getObject("id", UUID.class), rs.getTimestamp("locked_at").toInstant()),
                    UuidV7.generate(clock), userId, key, method, path, hash,
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plus(props.ttl())));
            if (!inserted.isEmpty()) {
                return inserted.get(0);
            }

            ExistingKey existing = findExisting(userId, key);
            if (existing == null) {
                continue; // deleted between our INSERT and SELECT (released or expired): try again
            }
            if (!existing.expiresAt().isAfter(now)) {
                jdbc.update("DELETE FROM idempotency_keys WHERE id = ? AND expires_at <= ?", existing.id(), Timestamp.from(now));
                continue; // an expired key is as good as unused
            }
            if (!existing.hash().equals(hash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used for a different request");
            }
            if ("COMPLETED".equals(existing.status())) {
                return new Replay(existing.responseStatus(), existing.responseBody());
            }
            if (existing.lockedAt().isBefore(now.minus(props.staleAfter()))) {
                // Take over a reservation whose request died. Blocks while the original's business
                // tx holds the row lock; if that tx then commits, status is COMPLETED, 0 rows match.
                List<Instant> takenOver = jdbc.query("""
                        UPDATE idempotency_keys SET locked_at = ?
                        WHERE id = ? AND status = 'IN_PROGRESS' AND locked_at = ?
                        RETURNING locked_at
                        """, (rs, n) -> rs.getTimestamp("locked_at").toInstant(),
                        Timestamp.from(now), existing.id(), Timestamp.from(existing.lockedAt()));
                if (!takenOver.isEmpty()) {
                    return new Reserved(existing.id(), takenOver.get(0));
                }
                continue;
            }
            return new InFlight();
        }
        return new InFlight();
    }

    private ExistingKey findExisting(UUID userId, String key) {
        List<ExistingKey> rows = jdbc.query("""
                SELECT id, request_hash, status, response_status, response_body::text AS body, locked_at, expires_at
                FROM idempotency_keys WHERE user_id = ? AND key = ?
                """, (rs, n) -> new ExistingKey(
                rs.getObject("id", UUID.class),
                rs.getString("request_hash"),
                rs.getString("status"),
                (Integer) rs.getObject("response_status"),
                rs.getString("body"),
                rs.getTimestamp("locked_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()), userId, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---- completion ----------------------------------------------------------------------

    /** First statement of the business tx: lock our key row and confirm nobody took it over. */
    private void assertStillOwned(Reserved reserved) {
        List<Instant> lockedAt = jdbc.query(
                "SELECT locked_at FROM idempotency_keys WHERE id = ? AND status = 'IN_PROGRESS' FOR UPDATE",
                (rs, n) -> rs.getTimestamp("locked_at").toInstant(), reserved.id());
        if (lockedAt.isEmpty() || !lockedAt.get(0).equals(reserved.lockedAt())) {
            throw new ReservationLostException();
        }
    }

    private void complete(Reserved reserved, HttpStatusCode status, String bodyJson, boolean mustOwn) {
        int updated = jdbc.update("""
                UPDATE idempotency_keys SET status = 'COMPLETED', response_status = ?, response_body = ?::jsonb
                WHERE id = ? AND status = 'IN_PROGRESS' AND locked_at = ?
                """, status.value(), bodyJson, reserved.id(), Timestamp.from(reserved.lockedAt()));
        if (updated == 0 && mustOwn) {
            throw new ReservationLostException();
        }
    }

    private void release(Reserved reserved) {
        separateTx.executeWithoutResult(s -> jdbc.update(
                "DELETE FROM idempotency_keys WHERE id = ? AND status = 'IN_PROGRESS' AND locked_at = ?",
                reserved.id(), Timestamp.from(reserved.lockedAt())));
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> replayed(Replay replay) {
        replays.increment();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(replay.status()).header(REPLAYED_HEADER, "true");
        if (replay.bodyJson() == null) {
            return (ResponseEntity<T>) builder.build();
        }
        builder.contentType(replay.status() >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON);
        try {
            // Jackson serializes by runtime type, so a JsonNode body is written as-is.
            return (ResponseEntity<T>) builder.body(objectMapper.readTree(replay.bodyJson()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is not valid JSON", e);
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private String toJson(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response is not serializable", e);
        }
    }

    /** Microsecond precision: what Postgres stores, so equality checks on locked_at hold. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
    }
}
