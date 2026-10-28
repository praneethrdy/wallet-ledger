package com.example.wallet.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Spec §9 test 14 (dashboard part). */
class DashboardIT extends ApiTest {

    @Test
    void summaryTotalsFlowsAndRecentTransactions() throws Exception {
        TestUser alice = customer();
        TestUser bob = customer();
        UUID main = ledger.fundedWallet(alice, 100_000);          // +100,000 in
        UUID savings = ledger.wallet(alice);
        UUID usd = ledger.wallet(alice, "USD");
        ledger.fund(usd, 2_500);                                   // USD: not in the INR flows
        UUID bobs = ledger.fundedWallet(bob, 50_000);

        transfer(alice, main, savings, 30_000, "INR");             // own wallets: neither in nor out
        transfer(alice, main, bobs, 12_000, "INR");                // -12,000 out
        transfer(bob, bobs, savings, 5_000, "INR");                // +5,000 in

        JsonNode summary = api.body(api.get(alice, "/api/v1/dashboard/summary").andExpect(status().isOk()));

        JsonNode totals = summary.path("totals");
        assertThat(totals).hasSize(2);
        assertThat(totals.get(0).path("currency").asText()).isEqualTo("INR");
        assertThat(totals.get(0).path("balance").asLong()).isEqualTo(100_000 - 12_000 + 5_000);
        assertThat(totals.get(1).path("currency").asText()).isEqualTo("USD");
        assertThat(totals.get(1).path("balance").asLong()).isEqualTo(2_500);

        assertThat(summary.path("thisMonth").path("currency").asText()).isEqualTo("INR");
        assertThat(summary.path("thisMonth").path("inflow").asLong()).isEqualTo(105_000);
        assertThat(summary.path("thisMonth").path("outflow").asLong()).isEqualTo(12_000);

        JsonNode cashFlow = summary.path("cashFlow30d");
        assertThat(cashFlow).hasSize(30);
        JsonNode today = cashFlow.get(29);
        assertThat(today.path("date").asText()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        assertThat(today.path("inflow").asLong()).isEqualTo(105_000);
        assertThat(today.path("outflow").asLong()).isEqualTo(12_000);
        assertThat(cashFlow.get(0).path("inflow").asLong()).isZero();

        JsonNode recent = summary.path("recentTransactions");
        assertThat(recent).hasSize(5); // Alice's 2 deposits (INR, USD) + 3 transfers touching her
        assertThat(recent.get(0).path("netAmount").asLong()).isEqualTo(5_000);   // newest: Bob -> Alice
        assertThat(recent.get(1).path("netAmount").asLong()).isEqualTo(-12_000);
        assertThat(recent.get(2).path("netAmount").asLong()).isZero();           // own-wallet move
    }

    @Test
    void recentTransactionsAreCappedAtEight() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        for (int i = 0; i < 10; i++) {
            ledger.fund(wallet, 100);
        }

        JsonNode summary = api.body(api.get(alice, "/api/v1/dashboard/summary"));
        assertThat(summary.path("recentTransactions")).hasSize(8);
    }

    @Test
    void emptyUserGetsZeros() throws Exception {
        JsonNode summary = api.body(api.get(customer(), "/api/v1/dashboard/summary").andExpect(status().isOk()));

        assertThat(summary.path("totals")).isEmpty();
        assertThat(summary.path("thisMonth").path("inflow").asLong()).isZero();
        assertThat(summary.path("cashFlow30d")).hasSize(30);
        assertThat(summary.path("recentTransactions")).isEmpty();
    }

    private void transfer(TestUser user, UUID from, UUID to, long amount, String currency) throws Exception {
        api.post(user, "/api/v1/transfers",
                        Map.of("fromWalletId", from, "toWalletId", to, "amount", amount, "currency", currency))
                .andExpect(status().isCreated());
    }
}
