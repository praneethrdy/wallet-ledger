package com.example.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.Money;
import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.example.wallet.testsupport.TestLedger;
import com.example.wallet.testsupport.TestUsers;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.user.Role;

/** The Java half of the double-entry rules, and derived balances. */
class LedgerServiceIT extends AbstractIntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    SystemAccounts systemAccounts;

    @Autowired
    TestUsers users;

    @Autowired
    TestLedger testLedger;

    @Autowired
    PlatformTransactionManager txManager;

    TransactionTemplate tx;
    TestUser alice;
    UUID walletA;
    UUID walletB;
    UUID usdWallet;
    UUID externalInr;
    UUID externalUsd;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        alice = users.create(Role.CUSTOMER);
        walletA = testLedger.wallet(alice);
        walletB = testLedger.wallet(alice);
        usdWallet = testLedger.wallet(alice, "USD");
        externalInr = systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, "INR");
        externalUsd = systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, "USD");
    }

    @Test
    void balancesAreDerivedFromEntriesAndSystemAccountsMayGoNegative() {
        testLedger.fund(walletA, 100_000);
        post(Posting.Leg.debit(walletA, inr(30_000)), Posting.Leg.credit(walletB, inr(30_000)));

        assertThat(ledger.balanceOf(walletA)).isEqualTo(70_000);
        assertThat(ledger.balanceOf(walletB)).isEqualTo(30_000);
        assertThat(ledger.balanceOf(externalInr)).isEqualTo(-100_000);
        assertThat(ledger.balancesOf(List.of(walletA, walletB, usdWallet)))
                .containsEntry(walletA, 70_000L).containsEntry(walletB, 30_000L).containsEntry(usdWallet, 0L);
    }

    @Test
    void postedTransactionHasReadableReference() {
        PostedTransaction posted = post(Posting.Leg.debit(externalInr, inr(1)), Posting.Leg.credit(walletA, inr(1)));

        assertThat(posted.reference()).matches("^TXN-\\d{8}-[0-9A-HJKMNP-TV-Z]{8}$");
    }

    @Test
    void rejectsUnequalDebitsAndCredits() {
        assertRejected(Posting.Leg.debit(walletA, inr(100)), Posting.Leg.credit(walletB, inr(99)));
    }

    @Test
    void rejectsPostingWithoutACredit() {
        assertRejected(Posting.Leg.debit(walletA, inr(100)));
    }

    @Test
    void rejectsPostingWithNoLegs() {
        assertRejected();
    }

    @Test
    void rejectsBalancedTotalsAcrossDifferentCurrencies() {
        assertRejected(Posting.Leg.debit(walletA, inr(100)), Posting.Leg.credit(usdWallet, Money.of(100, "USD")));
    }

    @Test
    void rejectsLegWhoseCurrencyDiffersFromItsAccount() {
        // Balanced in USD, but walletA is an INR account.
        assertRejected(Posting.Leg.debit(externalUsd, Money.of(100, "USD")), Posting.Leg.credit(walletA, Money.of(100, "USD")));
    }

    @Test
    void rejectsZeroAmountLegs() {
        assertRejected(Posting.Leg.debit(walletA, inr(0)), Posting.Leg.credit(walletB, inr(0)));
    }

    @Test
    void rejectsUnknownAccount() {
        assertRejected(Posting.Leg.debit(externalInr, inr(5)), Posting.Leg.credit(UUID.randomUUID(), inr(5)));
    }

    @Test
    void refusesToPostOutsideATransaction() {
        assertThatThrownBy(() -> ledger.post(posting(Posting.Leg.debit(externalInr, inr(1)), Posting.Leg.credit(walletA, inr(1)))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private PostedTransaction post(Posting.Leg... legs) {
        return tx.execute(s -> ledger.post(posting(legs)));
    }

    private Posting posting(Posting.Leg... legs) {
        return new Posting(TransactionType.TRANSFER, "test", alice.id(), null, null, Map.of(), List.of(legs));
    }

    private void assertRejected(Posting.Leg... legs) {
        assertThatThrownBy(() -> post(legs)).isInstanceOf(UnbalancedPostingException.class);
        assertThat(testLedger.count("SELECT count(*) FROM transactions")).isZero();
    }

    private static Money inr(long minor) {
        return Money.of(minor, "INR");
    }
}
