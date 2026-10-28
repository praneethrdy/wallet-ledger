package com.example.wallet.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;

/** Spec §9 test 6, plus deposit-level idempotency and amount checks. */
class WebhookIT extends ApiTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String OLD_SECRET = "test-webhook-secret-old";

    TestUser alice;
    UUID wallet;
    String depositId;
    String reference;

    @BeforeEach
    void createPendingDeposit() throws Exception {
        alice = customer();
        wallet = ledger.wallet(alice);
        var deposit = api.body(api.post(alice, "/api/v1/deposits",
                        Map.of("walletId", wallet, "amount", 50_000, "currency", "INR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")));
        depositId = deposit.path("id").asText();
        reference = deposit.path("providerReference").asText();
    }

    @Test
    void validSignedSuccessEventCreditsTheWallet() throws Exception {
        deliver(event("evt_ok", "deposit.succeeded", reference, 50_000), SECRET, now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));

        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
        api.get(alice, "/api/v1/deposits/" + depositId)
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
        assertThat(eventStatus("evt_ok")).isEqualTo("PROCESSED");
        assertThat(ledger.globalImbalance()).allSatisfy((c, imbalance) -> assertThat(imbalance).isZero());
    }

    @Test
    void previousSecretStillWorksDuringRotation() throws Exception {
        deliver(event("evt_rot", "deposit.succeeded", reference, 50_000), OLD_SECRET, now()).andExpect(status().isOk());
        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
    }

    @Test
    void badSignatureIs401AndIsRecordedAsRejected() throws Exception {
        deliver(event("evt_forged", "deposit.succeeded", reference, 50_000), "wrong-secret", now())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertThat(ledger.balance(wallet)).isZero();
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE status = 'REJECTED'")).isEqualTo(1);
        // A forged event must not occupy the real event id.
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE provider_event_id = 'evt_forged'")).isZero();
        deliver(event("evt_forged", "deposit.succeeded", reference, 50_000), SECRET, now()).andExpect(status().isOk());
        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
    }

    @Test
    void staleTimestampIs401EvenWithAValidSignature() throws Exception {
        deliver(event("evt_old", "deposit.succeeded", reference, 50_000), SECRET, now() - 6 * 60)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        assertThat(ledger.balance(wallet)).isZero();
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        long ts = now();
        String original = event("evt_t", "deposit.succeeded", reference, 1);
        String signature = HmacSignatureVerifier.sign(SECRET, ts, original.getBytes(StandardCharsets.UTF_8));
        String tampered = original.replace("\"amount\":1", "\"amount\":50000");

        deliverRaw(tampered, ts, signature).andExpect(status().isUnauthorized());
        assertThat(ledger.balance(wallet)).isZero();
    }

    @Test
    void missingHeadersAre401() throws Exception {
        mvc.perform(post("/api/v1/webhooks/payment-provider").contentType(MediaType.APPLICATION_JSON)
                        .content(event("evt_h", "deposit.succeeded", reference, 50_000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEventIsProcessedOnce() throws Exception {
        String body = event("evt_dup", "deposit.succeeded", reference, 50_000);
        deliver(body, SECRET, now()).andExpect(jsonPath("$.status").value("processed"));

        deliver(body, SECRET, now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'DEPOSIT'")).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateDeliveriesCreditOnce() throws Exception {
        String body = event("evt_race", "deposit.succeeded", reference, 50_000);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return deliver(body, SECRET, now()).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> r : results) {
            assertThat(r.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
    }

    @Test
    void differentEventIdForAnAlreadySettledDepositDoesNotCreditTwice() throws Exception {
        deliver(event("evt_a", "deposit.succeeded", reference, 50_000), SECRET, now());
        deliver(event("evt_b", "deposit.succeeded", reference, 50_000), SECRET, now())
                .andExpect(status().isOk());

        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
    }

    @Test
    void amountMismatchIsAcknowledgedButNotCredited() throws Exception {
        deliver(event("evt_amt", "deposit.succeeded", reference, 99_999), SECRET, now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));

        assertThat(ledger.balance(wallet)).isZero();
        assertThat(eventStatus("evt_amt")).isEqualTo("FAILED");
    }

    @Test
    void unknownDepositIsAcknowledgedAsFailed() throws Exception {
        deliver(event("evt_unknown", "deposit.succeeded", "mock_nope", 1), SECRET, now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    void failureEventMarksDepositFailedWithoutLedgerEntries() throws Exception {
        deliver(event("evt_fail", "deposit.failed", reference, 50_000), SECRET, now())
                .andExpect(jsonPath("$.status").value("processed"));

        api.get(alice, "/api/v1/deposits/" + depositId).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(ledger.count("SELECT count(*) FROM ledger_entries")).isZero();

        // A later "succeeded" for a failed deposit is not applied.
        deliver(event("evt_late", "deposit.succeeded", reference, 50_000), SECRET, now())
                .andExpect(jsonPath("$.status").value("failed"));
        assertThat(ledger.balance(wallet)).isZero();
    }

    @Test
    void depositIntoFrozenWalletIsCreditedBecauseTheMoneyAlreadyArrived() throws Exception {
        ledger.setStatus(wallet, "FROZEN");

        deliver(event("evt_frozen", "deposit.succeeded", reference, 50_000), SECRET, now()).andExpect(status().isOk());

        assertThat(ledger.balance(wallet)).isEqualTo(50_000);
    }

    // ---- deposits API --------------------------------------------------------------------

    @Test
    void depositsAreOwnerScopedAndValidated() throws Exception {
        TestUser mallory = customer();
        api.get(mallory, "/api/v1/deposits/" + depositId).andExpect(status().isNotFound());
        api.post(mallory, "/api/v1/deposits", Map.of("walletId", wallet, "amount", 1, "currency", "INR"))
                .andExpect(status().isNotFound());
        api.post(alice, "/api/v1/deposits", Map.of("walletId", wallet, "amount", 1, "currency", "USD"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        api.post(alice, "/api/v1/deposits", Map.of("walletId", wallet, "amount", 0, "currency", "INR"))
                .andExpect(status().isBadRequest());
        ledger.setStatus(wallet, "FROZEN");
        api.post(alice, "/api/v1/deposits", Map.of("walletId", wallet, "amount", 1, "currency", "INR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));
        api.post(admin(), "/api/v1/deposits", Map.of("walletId", wallet, "amount", 1, "currency", "INR"))
                .andExpect(status().isForbidden());
    }

    // ---- authentic but unusable payloads ------------------------------------------------

    @Test
    void signedButMalformedPayloadIs400AndKeptForForensics() throws Exception {
        deliver("this is not json", SECRET, now())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        deliver("{\"type\":\"deposit.succeeded\"}", SECRET, now()).andExpect(status().isBadRequest()); // no id

        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE status = 'REJECTED'")).isEqualTo(2);
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE payload ->> 'unparseable' = 'true'"))
                .as("non-JSON bodies are stored wrapped, not dropped").isEqualTo(1);
    }

    @Test
    void forgedGarbageIsStoredWrappedAndRejected() throws Exception {
        deliver("<xml>not even json</xml>", "attacker", now()).andExpect(status().isUnauthorized());

        assertThat(ledger.count("""
                SELECT count(*) FROM webhook_events
                WHERE status = 'REJECTED' AND event_type = 'unknown' AND payload ->> 'raw' LIKE '<xml>%'
                """)).isEqualTo(1);
    }

    @Test
    void unsupportedOrIncompleteEventsAreAcknowledgedButNotApplied() throws Exception {
        deliver("{\"id\":\"evt_type\",\"type\":\"refund.created\",\"data\":{\"providerReference\":\"" + reference + "\"}}",
                SECRET, now()).andExpect(jsonPath("$.status").value("failed"));
        deliver("{\"id\":\"evt_noref\",\"type\":\"deposit.succeeded\",\"data\":{\"amount\":1}}", SECRET, now())
                .andExpect(jsonPath("$.status").value("failed"));
        deliver("{\"id\":\"evt_noamt\",\"type\":\"deposit.succeeded\",\"data\":{\"providerReference\":\"" + reference + "\"}}",
                SECRET, now()).andExpect(jsonPath("$.status").value("failed"));

        assertThat(ledger.balance(wallet)).isZero();
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE status = 'FAILED' AND error IS NOT NULL"))
                .isEqualTo(3);
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions deliver(String body, String secret, long timestamp) throws Exception {
        String signature = HmacSignatureVerifier.sign(secret, timestamp, body.getBytes(StandardCharsets.UTF_8));
        return deliverRaw(body, timestamp, signature);
    }

    private ResultActions deliverRaw(String body, long timestamp, String signature) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/payment-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookController.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(WebhookController.SIGNATURE_HEADER, signature)
                .content(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String event(String id, String type, String providerReference, long amount) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"data\":{\"providerReference\":\""
                + providerReference + "\",\"amount\":" + amount + ",\"currency\":\"INR\"},\"extraField\":true}";
    }

    private String eventStatus(String providerEventId) {
        return ledger.count("SELECT count(*) FROM webhook_events WHERE provider_event_id = ? AND status = 'PROCESSED'",
                providerEventId) == 1 ? "PROCESSED"
                : ledger.count("SELECT count(*) FROM webhook_events WHERE provider_event_id = ? AND status = 'FAILED'",
                providerEventId) == 1 ? "FAILED" : "OTHER";
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }
}
