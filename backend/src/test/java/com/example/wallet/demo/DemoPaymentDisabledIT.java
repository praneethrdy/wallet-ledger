package com.example.wallet.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;

/** Production default: with the switch off, the demo payment endpoint behaves as if it didn't exist. */
@TestPropertySource(properties = "payments.demo.enabled=false")
class DemoPaymentDisabledIT extends ApiTest {

    @Test
    void itIsNotFoundAndChangesNothing() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        String depositId = api.body(api.post(alice, "/api/v1/deposits",
                Map.of("walletId", wallet, "amount", 25_000, "currency", "INR"))).path("id").asText();

        api.post(alice, "/api/v1/deposits/" + depositId + "/demo-payment", Map.of("outcome", "SUCCESS"))
                .andExpect(status().isNotFound());
        api.get(alice, "/api/v1/deposits/" + depositId).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(ledger.balance(wallet)).isZero();
    }
}
