package com.example.wallet.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.example.wallet.testsupport.TestUsers;
import com.example.wallet.user.Role;

/**
 * IdempotencyService failure paths, driven directly with a fake operation: what happens to
 * the key when the operation crashes, when the reservation is lost, and with empty bodies.
 */
class IdempotencyServiceIT extends AbstractIntegrationTest {

    @Autowired
    IdempotencyService idempotency;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    AuthUser caller;
    MockHttpServletRequest request;
    final Map<String, Object> body = Map.of("x", 1);

    @BeforeEach
    void setUp() {
        caller = new AuthUser(users.create(Role.CUSTOMER).id(), Role.CUSTOMER);
        request = new MockHttpServletRequest("POST", "/api/v1/test-operation");
    }

    @Test
    void unexpectedFailureReleasesTheKeySoARetryRunsAgain() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> idempotency.execute(caller, key, request, body, keyId -> {
            executions.incrementAndGet();
            throw new IllegalStateException("database hiccup");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(keyRows(key)).as("5xx: reservation released, nothing stored").isZero();

        ResponseEntity<String> retry = idempotency.execute(caller, key, request, body, keyId -> {
            executions.incrementAndGet();
            return ResponseEntity.ok("done");
        });

        assertThat(retry.getBody()).isEqualTo("done");
        assertThat(executions).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE key = ?", String.class, key))
                .isEqualTo("COMPLETED");
    }

    @Test
    void serverSideApiErrorAlsoReleasesTheKey() {
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> idempotency.execute(caller, key, request, body, keyId -> {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "boom");
        })).isInstanceOf(ApiException.class);

        assertThat(keyRows(key)).isZero();
    }

    @Test
    void lostReservationRollsBackTheBusinessChange() {
        String key = UUID.randomUUID().toString();

        // Simulate a takeover happening while our operation runs: the fencing token changes,
        // so completing must fail and everything the operation wrote must roll back.
        assertThatThrownBy(() -> idempotency.execute(caller, key, request, body, keyId -> {
            jdbc.update("INSERT INTO audit_log (action) VALUES ('WRITTEN_BY_OPERATION')");
            jdbc.update("UPDATE idempotency_keys SET locked_at = locked_at + interval '1 second' WHERE id = ?", keyId);
            return ResponseEntity.ok("should not be stored");
        }))
                .isInstanceOf(ReservationLostException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'WRITTEN_BY_OPERATION'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE key = ?", String.class, key))
                .as("the row stays with whoever took it over").isEqualTo("IN_PROGRESS");
    }

    @Test
    void emptyBodyResponsesAreReplayedWithoutABody() {
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        ResponseEntity<Void> first = idempotency.execute(caller, key, request, null, keyId -> {
            executions.incrementAndGet();
            return ResponseEntity.noContent().build();
        });
        ResponseEntity<Void> replay = idempotency.execute(caller, key, request, null, keyId -> {
            executions.incrementAndGet();
            return ResponseEntity.noContent().build();
        });

        assertThat(first.getStatusCode().value()).isEqualTo(204);
        assertThat(replay.getStatusCode().value()).isEqualTo(204);
        assertThat(replay.getHeaders().getFirst(IdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(replay.getBody()).isNull();
        assertThat(executions).hasValue(1);
    }

    @Test
    void missingKeyIsRejectedBeforeAnythingRuns() {
        assertThatThrownBy(() -> idempotency.execute(caller, null, request, body, keyId -> ResponseEntity.ok("x")))
                .isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Long.class)).isZero();
    }

    private long keyRows(String key) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key = ?", Long.class, key);
        return n == null ? 0 : n;
    }
}
