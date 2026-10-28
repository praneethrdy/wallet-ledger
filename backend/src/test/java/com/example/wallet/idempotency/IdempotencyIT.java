package com.example.wallet.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.transfer.TransferRequest;
import com.fasterxml.jackson.databind.JsonNode;

/** Spec §9 tests 3 and 4, plus replay of errors, takeover of stale keys and expiry. */
class IdempotencyIT extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RequestHasher hasher;

    @Autowired
    IdempotencyKeyCleanupJob cleanupJob;

    @Test
    void tenConcurrentDuplicatesProduceOneTransferAndIdenticalResponses() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 100_000);
        UUID to = ledger.wallet(customer());
        Map<String, Object> body = transfer(from, to, 10_000);
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return api.post(alice, "/api/v1/transfers", body, key).andReturn().getResponse();
            }));
        }
        start.countDown();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> f : futures) {
            responses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
        JsonNode first = json.readTree(responses.get(0).getContentAsString());
        assertThat(responses).allSatisfy(r -> assertThat(json.readTree(r.getContentAsString())).isEqualTo(first));
        assertThat(responses).filteredOn(r -> "true".equals(r.getHeader("Idempotent-Replayed"))).hasSize(9);
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'TRANSFER'")).isEqualTo(1);
        assertThat(ledger.balance(from)).isEqualTo(90_000);
    }

    @Test
    void sequentialRetryReplaysStoredResponseWithHeader() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(customer());
        String key = UUID.randomUUID().toString();

        JsonNode original = api.body(api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed")));
        JsonNode replay = api.body(api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")));

        assertThat(replay).isEqualTo(original);
        assertThat(ledger.balance(from)).isEqualTo(9_000);
    }

    @Test
    void transactionRecordsWhichKeyCreatedIt() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        String key = UUID.randomUUID().toString();
        String txId = api.body(api.post(alice, "/api/v1/transfers", transfer(from, ledger.wallet(customer()), 1), key))
                .path("id").asText();

        UUID linked = jdbc.queryForObject("SELECT idempotency_key_id FROM transactions WHERE id = ?::uuid", UUID.class, txId);
        UUID keyId = jdbc.queryForObject("SELECT id FROM idempotency_keys WHERE key = ?", UUID.class, key);
        assertThat(linked).isEqualTo(keyId);
    }

    @Test
    void sameKeyWithDifferentBodyIs422() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(customer());
        String key = UUID.randomUUID().toString();
        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key).andExpect(status().isCreated());

        api.post(alice, "/api/v1/transfers", transfer(from, to, 2_000), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(ledger.balance(from)).isEqualTo(9_000);
    }

    @Test
    void sameKeyOnADifferentEndpointIs422() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        String key = UUID.randomUUID().toString();
        api.post(alice, "/api/v1/transfers", transfer(from, ledger.wallet(customer()), 1_000), key);

        api.post(alice, "/api/v1/wallets", Map.of("name", "x"), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String sharedKey = "my-very-common-key";

        api.post(customer(), "/api/v1/wallets", Map.of("name", "A"), sharedKey).andExpect(status().isCreated());
        api.post(customer(), "/api/v1/wallets", Map.of("name", "B"), sharedKey)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void businessErrorsAreStoredAndReplayedToo() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 500);
        UUID to = ledger.wallet(customer());
        String key = UUID.randomUUID().toString();

        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        ledger.fund(from, 10_000); // now it WOULD succeed, but this key already has an answer

        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'TRANSFER'")).isZero();
    }

    @Test
    void freshInProgressKeyAnswers409AfterWaiting() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(customer());
        String key = UUID.randomUUID().toString();
        insertInProgress(alice, key, transferRequest(from, to, 1_000), Instant.now());

        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
        assertThat(ledger.balance(from)).isEqualTo(10_000);
    }

    @Test
    void staleInProgressKeyIsTakenOver() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(customer());
        String key = UUID.randomUUID().toString();
        // A request that reserved the key 2 minutes ago and then died.
        insertInProgress(alice, key, transferRequest(from, to, 1_000), Instant.now().minusSeconds(120));

        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000), key).andExpect(status().isCreated());

        assertThat(ledger.balance(from)).isEqualTo(9_000);
        assertThat(jdbc.queryForObject("SELECT status FROM idempotency_keys WHERE key = ?", String.class, key))
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredKeyCanBeReusedAndIsPurgedByCleanup() throws Exception {
        TestUser alice = customer();
        String key = UUID.randomUUID().toString();
        api.post(alice, "/api/v1/wallets", Map.of("name", "First"), key).andExpect(status().isCreated());
        jdbc.update("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute' WHERE key = ?", key);

        api.post(alice, "/api/v1/wallets", Map.of("name", "Second"), key)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.name").value("Second"));

        jdbc.update("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute'");
        assertThat(cleanupJob.purgeExpired()).isEqualTo(1);
        assertThat(ledger.count("SELECT count(*) FROM idempotency_keys")).isZero();
    }

    @Test
    void malformedKeyIs400() throws Exception {
        TestUser alice = customer();

        api.post(alice, "/api/v1/wallets", Map.of("name", "x"), "has spaces in it")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.post(alice, "/api/v1/wallets", Map.of("name", "x"), "k".repeat(256))
                .andExpect(status().isBadRequest());
    }

    private void insertInProgress(TestUser user, String key, Object body, Instant lockedAt) {
        String hash = hasher.hash("POST", "/api/v1/transfers", body);
        jdbc.update("""
                INSERT INTO idempotency_keys (id, user_id, key, request_method, request_path, request_hash,
                                              status, locked_at, created_at, expires_at)
                VALUES (gen_random_uuid(), ?, ?, 'POST', '/api/v1/transfers', ?, 'IN_PROGRESS', ?, ?, ?)
                """, user.id(), key, hash, Timestamp.from(lockedAt), Timestamp.from(lockedAt),
                Timestamp.from(lockedAt.plusSeconds(86_400)));
    }

    private static TransferRequest transferRequest(UUID from, UUID to, long amount) {
        return new TransferRequest(from, to, amount, "INR", null);
    }

    private static Map<String, Object> transfer(UUID from, UUID to, long amount) {
        return Map.of("fromWalletId", from, "toWalletId", to, "amount", amount, "currency", "INR");
    }
}
