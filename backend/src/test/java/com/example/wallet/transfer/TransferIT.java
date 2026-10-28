package com.example.wallet.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;

/** Transfer validation, frozen wallets (spec §9 test 13) and role checks (test 11, part). */
class TransferIT extends ApiTest {

    @Test
    void transferMovesMoneyAndReturnsBalancedLegs() throws Exception {
        TestUser alice = customer();
        TestUser bob = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(bob);

        JsonNode tx = api.body(api.post(alice, "/api/v1/transfers", transfer(from, to, 2_500, "lunch"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.description").value("lunch"))
                .andExpect(jsonPath("$.legs.length()").value(2)));

        assertThat(ledger.balance(from)).isEqualTo(7_500);
        assertThat(ledger.balance(to)).isEqualTo(2_500);
        JsonNode debit = tx.path("legs").get(0);
        JsonNode credit = tx.path("legs").get(1);
        assertThat(debit.path("direction").asText()).isEqualTo("DEBIT");
        assertThat(debit.path("accountId").asText()).isEqualTo(from.toString());
        assertThat(credit.path("direction").asText()).isEqualTo("CREDIT");
        // Bob's private wallet name is not shown to Alice.
        assertThat(credit.path("accountName").asText()).isEqualTo("Another user's wallet");
        assertThat(ledger.globalImbalance()).allSatisfy((currency, imbalance) -> assertThat(imbalance).isZero());
    }

    @Test
    void receiverSeesTheTransactionWithTheSenderMasked() throws Exception {
        TestUser alice = customer();
        TestUser bob = customer();
        UUID from = ledger.fundedWallet(alice, 10_000);
        UUID to = ledger.wallet(bob);
        String txId = api.body(api.post(alice, "/api/v1/transfers", transfer(from, to, 1_000, null))).path("id").asText();

        JsonNode seenByBob = api.body(api.get(bob, "/api/v1/transactions/" + txId).andExpect(status().isOk()));

        assertThat(seenByBob.path("legs").get(0).path("accountName").asText()).isEqualTo("Another user's wallet");
        assertThat(seenByBob.path("legs").get(1).path("accountName").asText()).isEqualTo("Test INR wallet");
        api.get(customer(), "/api/v1/transactions/" + txId).andExpect(status().isNotFound());
    }

    @Test
    void insufficientFundsIs422AndPostsNothing() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);
        UUID to = ledger.wallet(customer());

        api.post(alice, "/api/v1/transfers", transfer(from, to, 1_001, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(ledger.balance(from)).isEqualTo(1_000);
        assertThat(ledger.count("SELECT count(*) FROM transactions WHERE type = 'TRANSFER'")).isZero();
    }

    @Test
    void exactBalanceCanBeSpent() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);

        api.post(alice, "/api/v1/transfers", transfer(from, ledger.wallet(customer()), 1_000, null))
                .andExpect(status().isCreated());
        assertThat(ledger.balance(from)).isZero();
    }

    @Test
    void sameWalletTransferIs422() throws Exception {
        TestUser alice = customer();
        UUID wallet = ledger.fundedWallet(alice, 1_000);

        api.post(alice, "/api/v1/transfers", transfer(wallet, wallet, 100, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_WALLET_TRANSFER"));
    }

    @Test
    void currencyMismatchIs422() throws Exception {
        TestUser alice = customer();
        UUID inr = ledger.fundedWallet(alice, 1_000);
        UUID usd = ledger.wallet(customer(), "USD");

        api.post(alice, "/api/v1/transfers", transfer(inr, usd, 100, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        api.post(alice, "/api/v1/transfers", Map.of("fromWalletId", inr, "toWalletId", usd, "amount", 100, "currency", "USD"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void unknownCurrencyCodeIs400() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);

        api.post(alice, "/api/v1/transfers", Map.of("fromWalletId", from, "toWalletId", ledger.wallet(customer()),
                        "amount", 100, "currency", "ABC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotSpendFromSomeoneElsesWallet() throws Exception {
        TestUser alice = customer();
        TestUser mallory = customer();
        UUID alicesWallet = ledger.fundedWallet(alice, 10_000);

        api.post(mallory, "/api/v1/transfers", transfer(alicesWallet, ledger.wallet(mallory), 5_000, null))
                .andExpect(status().isNotFound());
        assertThat(ledger.balance(alicesWallet)).isEqualTo(10_000);
    }

    @Test
    void unknownDestinationIs404() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);

        api.post(alice, "/api/v1/transfers", transfer(from, UUID.randomUUID(), 100, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void frozenWalletBlocksTransfersOut() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);
        ledger.setStatus(from, "FROZEN");

        api.post(alice, "/api/v1/transfers", transfer(from, ledger.wallet(customer()), 100, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));
    }

    @Test
    void frozenWalletBlocksTransfersIn() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);
        UUID to = ledger.wallet(customer());
        ledger.setStatus(to, "FROZEN");

        api.post(alice, "/api/v1/transfers", transfer(from, to, 100, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));
        assertThat(ledger.balance(from)).isEqualTo(1_000);
    }

    @Test
    void adminAndSupportCannotTransfer() throws Exception {
        TestUser admin = admin();
        UUID adminWallet = ledger.fundedWallet(admin, 1_000);
        UUID target = ledger.wallet(customer());

        api.post(admin, "/api/v1/transfers", transfer(adminWallet, target, 100, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.post(support(), "/api/v1/transfers", transfer(adminWallet, target, 100, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidAmountsAreRejected() throws Exception {
        TestUser alice = customer();
        UUID from = ledger.fundedWallet(alice, 1_000);
        UUID to = ledger.wallet(customer());

        api.post(alice, "/api/v1/transfers", transfer(from, to, 0, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
        api.post(alice, "/api/v1/transfers", transfer(from, to, -5, null)).andExpect(status().isBadRequest());
    }

    static Map<String, Object> transfer(UUID from, UUID to, long amount, String note) {
        return note == null
                ? Map.of("fromWalletId", from, "toWalletId", to, "amount", amount, "currency", "INR")
                : Map.of("fromWalletId", from, "toWalletId", to, "amount", amount, "currency", "INR", "note", note);
    }
}
