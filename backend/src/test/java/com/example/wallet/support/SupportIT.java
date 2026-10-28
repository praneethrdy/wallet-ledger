package com.example.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Read-only support API: works for SUPPORT and ADMIN, forbidden for customers, always audited. */
class SupportIT extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    TestUser support;
    TestUser alice;
    UUID aliceWallet;
    String transferReference;
    String transferId;

    @BeforeEach
    void data() throws Exception {
        support = support();
        alice = customer();
        aliceWallet = ledger.fundedWallet(alice, 10_000);
        JsonNode transfer = api.body(api.post(alice, "/api/v1/transfers",
                Map.of("fromWalletId", aliceWallet, "toWalletId", ledger.wallet(customer()), "amount", 400, "currency", "INR")));
        transferReference = transfer.path("reference").asText();
        transferId = transfer.path("id").asText();
    }

    @Test
    void findUserByEmailAndSeeTheirWalletsWithRealNames() throws Exception {
        JsonNode found = api.body(api.get(support, "/api/v1/support/users?email=" + alice.email().toUpperCase())
                .andExpect(status().isOk()));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).path("id").asText()).isEqualTo(alice.id().toString());

        api.get(support, "/api/v1/support/users/" + alice.id())
                .andExpect(jsonPath("$.email").value(alice.email()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        api.get(support, "/api/v1/support/users/" + alice.id() + "/wallets")
                .andExpect(jsonPath("$[0].id").value(aliceWallet.toString()))
                .andExpect(jsonPath("$[0].name").value("Test INR wallet"))
                .andExpect(jsonPath("$[0].balance").value(9_600));
        api.get(support, "/api/v1/support/users/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void searchTransactionsByReferenceAndWallet() throws Exception {
        api.get(support, "/api/v1/support/transactions?reference=" + transferReference.toLowerCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reference").value(transferReference))
                .andExpect(jsonPath("$.items[0].amount").value(400));

        JsonNode byWallet = api.body(api.get(support, "/api/v1/support/transactions?walletId=" + aliceWallet));
        assertThat(byWallet.path("items")).hasSize(2); // deposit + transfer
        assertThat(byWallet.path("items").get(0).path("netAmount").asLong()).isEqualTo(-400);
        api.get(support, "/api/v1/support/transactions?type=DEPOSIT")
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void transactionDetailShowsEveryLegUnderItsRealNameAndIsAudited() throws Exception {
        api.get(support, "/api/v1/support/transactions/" + transferId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(transferReference))
                .andExpect(jsonPath("$.legs.length()").value(2))
                .andExpect(jsonPath("$.legs[?(@.direction == 'CREDIT')].accountName").value("Test INR wallet"));
        assertThat(ledger.count("SELECT count(*) FROM audit_log WHERE action = 'SUPPORT_VIEW_TRANSACTION' AND entity_id = ?",
                transferId)).isEqualTo(1);

        api.get(support, "/api/v1/support/transactions/" + UUID.randomUUID()).andExpect(status().isNotFound());
        api.get(alice, "/api/v1/support/transactions/" + transferId).andExpect(status().isForbidden());
    }

    @Test
    void webhookEventsAreListedByStatus() throws Exception {
        jdbc.update("""
                INSERT INTO webhook_events (id, provider, provider_event_id, event_type, payload, status, error)
                VALUES (gen_random_uuid(), 'mockpay', 'rejected:x', 'deposit.succeeded', '{"a":1}', 'REJECTED', 'INVALID_SIGNATURE')
                """);

        api.get(support, "/api/v1/support/webhook-events?status=REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].error").value("INVALID_SIGNATURE"))
                .andExpect(jsonPath("$.items[0].payload.a").value(1));
        api.get(support, "/api/v1/support/webhook-events?status=PROCESSED").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void everyAccessIsAudited() throws Exception {
        api.get(support, "/api/v1/support/users/" + alice.id());
        api.get(support, "/api/v1/support/users/" + alice.id() + "/wallets");
        api.get(support, "/api/v1/support/transactions?walletId=" + aliceWallet);

        assertThat(ledger.count("""
                SELECT count(*) FROM audit_log
                WHERE actor_user_id = ? AND action LIKE 'SUPPORT_%' AND correlation_id IS NOT NULL
                """, support.id())).isEqualTo(3);
        assertThat(ledger.count("SELECT count(*) FROM audit_log WHERE action = 'SUPPORT_VIEW_USER' AND entity_id = ?",
                alice.id().toString())).isEqualTo(1);
    }

    @Test
    void adminMayUseItCustomersMayNot() throws Exception {
        api.get(admin(), "/api/v1/support/users/" + alice.id()).andExpect(status().isOk());
        api.get(alice, "/api/v1/support/users/" + alice.id()).andExpect(status().isForbidden());
        api.get(alice, "/api/v1/support/transactions").andExpect(status().isForbidden());
    }
}
