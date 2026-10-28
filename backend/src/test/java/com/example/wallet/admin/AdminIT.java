package com.example.wallet.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Freeze/unfreeze, wallet search, overview; admin-only access (spec §9 test 11, part). */
class AdminIT extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void freezeBlocksTransfersAndUnfreezeRestoresThem() throws Exception {
        TestUser admin = admin();
        TestUser alice = customer();
        UUID wallet = ledger.fundedWallet(alice, 5_000);
        UUID target = ledger.wallet(customer());
        Map<String, Object> transfer = Map.of("fromWalletId", wallet, "toWalletId", target, "amount", 100, "currency", "INR");

        api.post(admin, "/api/v1/admin/wallets/" + wallet + "/freeze", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.ownerEmail").value(alice.email()))
                .andExpect(jsonPath("$.balance").value(5_000));
        api.post(alice, "/api/v1/transfers", transfer)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));

        api.post(admin, "/api/v1/admin/wallets/" + wallet + "/unfreeze", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        api.post(alice, "/api/v1/transfers", transfer).andExpect(status().isCreated());
    }

    @Test
    void freezingTwiceIsANoOpAndAuditedOnce() throws Exception {
        TestUser admin = admin();
        UUID wallet = ledger.wallet(customer());

        api.post(admin, "/api/v1/admin/wallets/" + wallet + "/freeze", null).andExpect(status().isOk());
        api.post(admin, "/api/v1/admin/wallets/" + wallet + "/freeze", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        assertThat(ledger.count("SELECT count(*) FROM audit_log WHERE action = 'WALLET_FROZEN'")).isEqualTo(1);
    }

    @Test
    void freezeUnknownOrSystemAccountIs404() throws Exception {
        TestUser admin = admin();
        UUID systemAccount = jdbc.queryForObject("SELECT id FROM accounts WHERE type = 'SYSTEM' LIMIT 1", UUID.class);

        api.post(admin, "/api/v1/admin/wallets/" + UUID.randomUUID() + "/freeze", null).andExpect(status().isNotFound());
        api.post(admin, "/api/v1/admin/wallets/" + systemAccount + "/freeze", null).andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersByEmailAndStatus() throws Exception {
        TestUser admin = admin();
        TestUser alice = customer();
        TestUser bob = customer();
        UUID aliceWallet = ledger.fundedWallet(alice, 1_234);
        UUID bobWallet = ledger.wallet(bob);
        ledger.setStatus(bobWallet, "FROZEN");

        JsonNode byEmail = api.body(api.get(admin, "/api/v1/admin/wallets?email=" + alice.email().substring(0, 20).toUpperCase())
                .andExpect(status().isOk()));
        assertThat(byEmail.path("items")).hasSize(1);
        assertThat(byEmail.path("items").get(0).path("id").asText()).isEqualTo(aliceWallet.toString());
        assertThat(byEmail.path("items").get(0).path("balance").asLong()).isEqualTo(1_234);

        JsonNode frozen = api.body(api.get(admin, "/api/v1/admin/wallets?status=FROZEN"));
        assertThat(frozen.path("items")).extracting(w -> w.path("id").asText()).containsExactly(bobWallet.toString());

        JsonNode wildcard = api.body(api.get(admin, "/api/v1/admin/wallets?email=%25"));
        assertThat(wildcard.path("items")).as("'%' is matched literally, not as a wildcard").isEmpty();
    }

    @Test
    void searchPaginates() throws Exception {
        TestUser admin = admin();
        TestUser alice = customer();
        for (int i = 0; i < 3; i++) {
            ledger.wallet(alice);
        }

        JsonNode first = api.body(api.get(admin, "/api/v1/admin/wallets?limit=2"));
        JsonNode second = api.body(api.get(admin, "/api/v1/admin/wallets?limit=2&cursor=" + first.path("nextCursor").asText()));

        assertThat(first.path("items")).hasSize(2);
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void overviewSummarisesToday() throws Exception {
        TestUser admin = admin();
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(customer());
        api.post(alice, "/api/v1/transfers", Map.of("fromWalletId", from, "toWalletId", to, "amount", 700, "currency", "INR"));
        api.post(alice, "/api/v1/transfers", Map.of("fromWalletId", from, "toWalletId", to, "amount", 300, "currency", "INR"));

        api.get(admin, "/api/v1/admin/overview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfersToday").value(2))
                .andExpect(jsonPath("$.volumeToday[0].currency").value("INR"))
                .andExpect(jsonPath("$.volumeToday[0].amount").value(1_000))
                .andExpect(jsonPath("$.failedWebhooks24h").value(0));
    }

    @Test
    void adminEndpointsAreAdminOnly() throws Exception {
        UUID wallet = ledger.wallet(customer());
        for (TestUser user : new TestUser[] {customer(), support()}) {
            api.get(user, "/api/v1/admin/wallets").andExpect(status().isForbidden());
            api.get(user, "/api/v1/admin/overview").andExpect(status().isForbidden());
            api.post(user, "/api/v1/admin/wallets/" + wallet + "/freeze", null).andExpect(status().isForbidden());
        }
    }
}
