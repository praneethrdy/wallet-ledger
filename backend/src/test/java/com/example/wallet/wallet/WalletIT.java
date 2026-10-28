package com.example.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Wallets, entries pagination with running balances (spec §9 test 14, part), balance history. */
class WalletIT extends ApiTest {

    @Test
    void createDefaultsToInrWithZeroBalance() throws Exception {
        TestUser alice = customer();

        api.post(alice, "/api/v1/wallets", Map.of("name", "Main"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Main"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void createRejectsUnsupportedOrMalformedCurrency() throws Exception {
        TestUser alice = customer();

        api.post(alice, "/api/v1/wallets", Map.of("name", "Pounds", "currency", "GBP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.post(alice, "/api/v1/wallets", Map.of("name", "Bad", "currency", "inr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listShowsOnlyCallersWalletsWithDerivedBalances() throws Exception {
        TestUser alice = customer();
        TestUser bob = customer();
        UUID a1 = ledger.fundedWallet(alice, 5_000);
        UUID a2 = ledger.wallet(alice, "USD");
        ledger.fundedWallet(bob, 9_999);

        JsonNode list = api.body(api.get(alice, "/api/v1/wallets").andExpect(status().isOk()));

        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("id").asText()).isEqualTo(a1.toString());
        assertThat(list.get(0).path("balance").asLong()).isEqualTo(5_000);
        assertThat(list.get(1).path("id").asText()).isEqualTo(a2.toString());
        assertThat(list.get(1).path("currency").asText()).isEqualTo("USD");
    }

    @Test
    void foreignWalletLooksExactlyLikeAMissingOne() throws Exception {
        TestUser alice = customer();
        TestUser bob = customer();
        UUID bobsWallet = ledger.fundedWallet(bob, 1_000);

        JsonNode foreign = api.body(api.get(alice, "/api/v1/wallets/{id}", bobsWallet).andExpect(status().isNotFound()));
        JsonNode missing = api.body(api.get(alice, "/api/v1/wallets/{id}", UUID.randomUUID()).andExpect(status().isNotFound()));

        assertThat(foreign.path("code")).isEqualTo(missing.path("code"));
        assertThat(foreign.path("detail")).isEqualTo(missing.path("detail"));
        api.get(alice, "/api/v1/wallets/{id}/entries", bobsWallet).andExpect(status().isNotFound());
        api.get(alice, "/api/v1/wallets/{id}/balance-history", bobsWallet).andExpect(status().isNotFound());
    }

    @Test
    void supportStaffHoldNoWallets() throws Exception {
        api.get(support(), "/api/v1/wallets").andExpect(status().isForbidden());
    }

    @Test
    void entriesArePagedNewestFirstWithCorrectRunningBalances() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        long[] deposits = {1_000, 2_000, 3_000, 4_000, 5_000};
        for (long amount : deposits) {
            ledger.fund(wallet, amount);
        }

        List<JsonNode> all = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String path = "/api/v1/wallets/" + wallet + "/entries?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = api.body(api.get(alice, path).andExpect(status().isOk()));
            page.path("items").forEach(all::add);
            cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(all).extracting(e -> e.path("amount").asLong()).containsExactly(5_000L, 4_000L, 3_000L, 2_000L, 1_000L);
        assertThat(all).extracting(e -> e.path("runningBalance").asLong())
                .containsExactly(15_000L, 10_000L, 6_000L, 3_000L, 1_000L);
        assertThat(all).allSatisfy(e -> assertThat(e.path("direction").asText()).isEqualTo("CREDIT"));
    }

    @Test
    void cursorIsStableWhenNewEntriesArriveBetweenPages() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        ledger.fund(wallet, 100);
        ledger.fund(wallet, 200);
        ledger.fund(wallet, 300);

        JsonNode first = api.body(api.get(alice, "/api/v1/wallets/" + wallet + "/entries?limit=2"));
        ledger.fund(wallet, 999); // arrives at the top, must not shift page 2
        JsonNode second = api.body(api.get(alice, "/api/v1/wallets/" + wallet + "/entries?limit=2&cursor="
                + first.path("nextCursor").asText()));

        assertThat(first.path("items")).extracting(e -> e.path("amount").asLong()).containsExactly(300L, 200L);
        assertThat(second.path("items")).extracting(e -> e.path("amount").asLong()).containsExactly(100L);
        assertThat(second.path("items").get(0).path("runningBalance").asLong()).isEqualTo(100);
        assertThat(second.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void invalidCursorOrLimitIs400() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);

        api.get(alice, "/api/v1/wallets/" + wallet + "/entries?cursor=not-a-cursor")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.get(alice, "/api/v1/wallets/" + wallet + "/entries?limit=101").andExpect(status().isBadRequest());
    }

    @Test
    void balanceHistoryEndsAtTheCurrentBalance() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.wallet(alice);
        ledger.fund(wallet, 7_000);
        ledger.fund(wallet, 500);

        JsonNode history = api.body(api.get(alice, "/api/v1/wallets/" + wallet + "/balance-history?days=7")
                .andExpect(status().isOk()));

        assertThat(history).hasSize(7);
        assertThat(history.get(6).path("balance").asLong()).isEqualTo(7_500);
        assertThat(history.get(0).path("balance").asLong()).isZero();
    }
}
