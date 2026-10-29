package com.example.wallet.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;

/** The simulated payment a public demo uses: same signed-webhook settlement, own deposits only. */
class DemoPaymentIT extends ApiTest {

    TestUser alice;
    UUID wallet;
    String depositId;

    @BeforeEach
    void startDeposit() throws Exception {
        alice = customer();
        wallet = ledger.wallet(alice);
        depositId = api.body(api.post(alice, "/api/v1/deposits",
                Map.of("walletId", wallet, "amount", 25_000, "currency", "INR"))).path("id").asText();
    }

    @Test
    void successCreditsExactlyOnceThroughTheWebhookPipeline() throws Exception {
        pay(alice, "SUCCESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
        pay(alice, "SUCCESS").andExpect(jsonPath("$.status").value("SUCCEEDED")); // a second click

        assertThat(ledger.balance(wallet)).isEqualTo(25_000);
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'DEPOSIT'")).isEqualTo(1);
        // It went through the real pipeline: a verified, processed provider event...
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE status = 'PROCESSED'")).isEqualTo(1);
        // ...and the simulation itself is on the record.
        assertThat(ledger.count("SELECT count(*) FROM audit_log WHERE action = 'DEMO_PAYMENT_COMPLETED'")).isEqualTo(1);
        assertThat(ledger.globalImbalance()).allSatisfy((c, imbalance) -> assertThat(imbalance).isZero());
    }

    @Test
    void failureMarksTheDepositFailedAndPostsNothing() throws Exception {
        pay(alice, "FAILURE").andExpect(jsonPath("$.status").value("FAILED"));
        pay(alice, "SUCCESS").andExpect(jsonPath("$.status").value("FAILED")); // settled: no change
        assertThat(ledger.balance(wallet)).isZero();
    }

    @Test
    void onlyTheOwnerCanPayTheirDeposit() throws Exception {
        pay(customer(), "SUCCESS").andExpect(status().isNotFound());
        pay(admin(), "SUCCESS").andExpect(status().isForbidden()); // admins can't move money
        assertThat(ledger.balance(wallet)).isZero();
    }

    @Test
    void anUnknownOutcomeIsRejected() throws Exception {
        pay(alice, "MAYBE").andExpect(status().isBadRequest());
    }

    org.springframework.test.web.servlet.ResultActions pay(TestUser user, String outcome) throws Exception {
        return api.post(user, "/api/v1/deposits/" + depositId + "/demo-payment", Map.of("outcome", outcome));
    }
}
