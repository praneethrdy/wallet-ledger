package com.example.wallet.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.example.wallet.deposit.DepositService;
import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;

/**
 * When OUR side fails mid-processing, the webhook answers 500 so the provider retries, the
 * event is kept as FAILED, nothing is half-applied, and the retry of the same event id is then
 * processed normally (not mistaken for a duplicate).
 */
class WebhookRetryIT extends ApiTest {

    @MockitoSpyBean
    DepositService deposits;

    @Test
    void failedProcessingIsRetriedByTheProviderAndThenApplied() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        String reference = api.body(api.post(alice, "/api/v1/deposits",
                Map.of("walletId", wallet, "amount", 7_000, "currency", "INR"))).path("providerReference").asText();
        String event = "{\"id\":\"evt_retry\",\"type\":\"deposit.succeeded\",\"data\":{\"providerReference\":\""
                + reference + "\",\"amount\":7000,\"currency\":\"INR\"}}";

        // Stub the spy behind Spring's transactional proxy (calling through the proxy would
        // demand a surrounding transaction). The spy is reset automatically after the test.
        DepositService spy = AopTestUtils.getUltimateTargetObject(deposits);
        doThrow(new IllegalStateException("database hiccup"))
                .doCallRealMethod()
                .when(spy).settleSucceeded(any(), anyLong(), any());

        deliver(event).andExpect(status().isInternalServerError());
        assertThat(ledger.balance(wallet)).isZero();
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE provider_event_id = 'evt_retry' AND status = 'FAILED'"))
                .isEqualTo(1);

        deliver(event).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        assertThat(ledger.balance(wallet)).isEqualTo(7_000);
        assertThat(ledger.count("SELECT count(*) FROM webhook_events WHERE provider_event_id = 'evt_retry'"))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions deliver(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return mvc.perform(post("/api/v1/webhooks/payment-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .header(WebhookController.TIMESTAMP_HEADER, Long.toString(ts))
                .header(WebhookController.SIGNATURE_HEADER, HmacSignatureVerifier.sign("test-webhook-secret", ts, bytes))
                .content(bytes));
    }
}
