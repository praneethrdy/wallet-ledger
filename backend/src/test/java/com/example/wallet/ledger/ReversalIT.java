package com.example.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Spec §9 test 7 (reversal) and the reversal part of test 11 (RBAC). */
class ReversalIT extends ApiTest {

    @Autowired
    JdbcTemplate jdbc;

    TestUser alice;
    TestUser bob;
    TestUser admin;
    UUID aliceWallet;
    UUID bobWallet;
    String transferId;

    @BeforeEach
    void transferToReverse() throws Exception {
        alice = customer();
        bob = customer();
        admin = admin();
        aliceWallet = ledger.fundedWallet(alice, 10_000);
        bobWallet = ledger.wallet(bob);
        transferId = api.body(api.post(alice, "/api/v1/transfers",
                Map.of("fromWalletId", aliceWallet, "toWalletId", bobWallet, "amount", 3_000, "currency", "INR")))
                .path("id").asText();
    }

    @Test
    void reversalPostsMirrorLegsAndLinksBothWays() throws Exception {
        JsonNode reversal = api.body(reverse(admin, transferId, "Customer sent to wrong wallet")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.reversalOfTransactionId").value(transferId)));

        JsonNode legs = reversal.path("legs");
        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).path("accountId").asText()).isEqualTo(aliceWallet.toString());
        assertThat(legs.get(0).path("direction").asText()).isEqualTo("CREDIT");
        assertThat(legs.get(1).path("accountId").asText()).isEqualTo(bobWallet.toString());
        assertThat(legs.get(1).path("direction").asText()).isEqualTo("DEBIT");
        assertThat(reversal.path("description").asText()).contains("Customer sent to wrong wallet");

        assertThat(ledger.balance(aliceWallet)).isEqualTo(10_000);
        assertThat(ledger.balance(bobWallet)).isZero();
        api.get(alice, "/api/v1/transactions/" + transferId)
                .andExpect(jsonPath("$.reversedByTransactionId").value(reversal.path("id").asText()));
        assertThat(ledger.count("SELECT count(*) FROM audit_log WHERE action = 'TRANSACTION_REVERSED'")).isEqualTo(1);
        assertThat(ledger.globalImbalance()).allSatisfy((c, imbalance) -> assertThat(imbalance).isZero());
    }

    @Test
    void originalRowsAreUnchanged() throws Exception {
        List<Map<String, Object>> before = snapshot();

        reverse(admin, transferId, "fix").andExpect(status().isCreated());

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void secondReversalIsRejected() throws Exception {
        reverse(admin, transferId, "first").andExpect(status().isCreated());

        reverse(admin, transferId, "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVERSED"));
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'REVERSAL'")).isEqualTo(1);
    }

    @Test
    void aReversalCannotBeReversed() throws Exception {
        String reversalId = api.body(reverse(admin, transferId, "first")).path("id").asText();

        reverse(admin, reversalId, "undo the undo")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REVERSAL_NOT_ALLOWED"));
    }

    @Test
    void reversalThatWouldMakeAWalletNegativeIsRefused() throws Exception {
        // Bob already spent the money he received.
        api.post(bob, "/api/v1/transfers", Map.of("fromWalletId", bobWallet, "toWalletId", ledger.wallet(customer()),
                "amount", 2_500, "currency", "INR")).andExpect(status().isCreated());

        reverse(admin, transferId, "too late")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS_FOR_REVERSAL"));
        assertThat(ledger.balance(bobWallet)).isEqualTo(500);
    }

    @Test
    void depositCanBeReversedEvenOnAFrozenWallet() throws Exception {
        UUID carolWallet = ledger.wallet(customer());
        String depositTx = ledger.fund(carolWallet, 5_000).toString();
        ledger.setStatus(carolWallet, "FROZEN");

        // A staff correction (e.g. chargeback), not customer spending: allowed while frozen.
        reverse(admin, depositTx, "chargeback").andExpect(status().isCreated());

        assertThat(ledger.balance(carolWallet)).isZero();
    }

    @Test
    void unknownTransactionIs404() throws Exception {
        reverse(admin, UUID.randomUUID().toString(), "x").andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanReverse() throws Exception {
        reverse(support(), transferId, "x")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        reverse(alice, transferId, "x").andExpect(status().isForbidden());
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'REVERSAL'")).isZero();
    }

    @Test
    void reasonIsRequired() throws Exception {
        api.post(admin, "/api/v1/admin/transactions/" + transferId + "/reverse", Map.of("reason", " "))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions reverse(TestUser user, String txId, String reason) throws Exception {
        return api.post(user, "/api/v1/admin/transactions/" + txId + "/reverse", Map.of("reason", reason));
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("""
                SELECT t.id, t.type, t.reference, t.description, t.created_at, e.id AS entry_id,
                       e.account_id, e.direction, e.amount, e.created_at AS entry_created
                FROM transactions t JOIN ledger_entries e ON e.transaction_id = t.id
                WHERE t.id = ?::uuid ORDER BY e.id
                """, transferId);
    }
}
