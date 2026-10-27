package com.example.wallet.ledger;

import static com.example.wallet.testsupport.LedgerSql.count;
import static com.example.wallet.testsupport.LedgerSql.insertLeg;
import static com.example.wallet.testsupport.LedgerSql.insertTransaction;
import static com.example.wallet.testsupport.LedgerSql.insertUser;
import static com.example.wallet.testsupport.LedgerSql.insertWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.wallet.testsupport.AbstractIntegrationTest;

/**
 * Spec §9 test 9: the deferred constraint trigger rejects unbalanced transactions AT COMMIT.
 *
 * <p>Pattern in every negative test: the INSERTs succeed (the check is deferred, so a
 * half-written transaction is allowed mid-flight), and {@code commit()} is what fails.
 * Afterwards nothing from that DB transaction is persisted.
 */
class DoubleEntryTriggerIT extends AbstractIntegrationTest {

    private Connection conn;
    private UUID user;
    private UUID inrA;
    private UUID inrB;
    private UUID inrC;
    private UUID usdD;

    @BeforeEach
    void setUp() throws SQLException {
        conn = appConnection();
        user = insertUser(conn, "CUSTOMER");
        inrA = insertWallet(conn, user, "INR");
        inrB = insertWallet(conn, user, "INR");
        inrC = insertWallet(conn, user, "INR");
        usdD = insertWallet(conn, user, "USD");
        conn.setAutoCommit(false);
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    // ---- balanced: must commit -------------------------------------------------------

    @Test
    void balancedTwoLegTransactionCommits() throws SQLException {
        UUID tx = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, tx, inrA, "DEBIT", 10_000, "INR");
        insertLeg(conn, tx, inrB, "CREDIT", 10_000, "INR");
        conn.commit();

        assertThat(count(conn, "SELECT count(*) FROM ledger_entries")).isEqualTo(2);
    }

    @Test
    void balancedMultiLegTransactionCommits() throws SQLException {
        UUID tx = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, tx, inrA, "DEBIT", 10_000, "INR");
        insertLeg(conn, tx, inrB, "CREDIT", 6_000, "INR");
        insertLeg(conn, tx, inrC, "CREDIT", 4_000, "INR");
        conn.commit();

        assertThat(count(conn, "SELECT count(*) FROM ledger_entries")).isEqualTo(3);
    }

    // ---- unbalanced: must fail at commit ---------------------------------------------

    @Test
    void debitWithoutCreditFailsAtCommit() throws SQLException {
        UUID tx = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, tx, inrA, "DEBIT", 10_000, "INR"); // succeeds: check is deferred

        assertCommitFailsAsUnbalanced();
    }

    @Test
    void mismatchedAmountsFailAtCommit() throws SQLException {
        UUID tx = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, tx, inrA, "DEBIT", 10_000, "INR");
        insertLeg(conn, tx, inrB, "CREDIT", 9_999, "INR");

        assertCommitFailsAsUnbalanced();
    }

    @Test
    void equalTotalsInDifferentCurrenciesFailAtCommit() throws SQLException {
        // 100 INR out, 100 USD in: sums match numerically but not per currency.
        UUID tx = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, tx, inrA, "DEBIT", 10_000, "INR");
        insertLeg(conn, tx, usdD, "CREDIT", 10_000, "USD");

        assertCommitFailsAsUnbalanced();
    }

    @Test
    void transactionWithNoLegsFailsAtCommit() throws SQLException {
        insertTransaction(conn, "TRANSFER", user);

        assertCommitFailsAsUnbalanced();
    }

    @Test
    void oneUnbalancedTransactionRollsBackTheWholeCommit() throws SQLException {
        UUID good = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, good, inrA, "DEBIT", 10_000, "INR");
        insertLeg(conn, good, inrB, "CREDIT", 10_000, "INR");

        UUID bad = insertTransaction(conn, "TRANSFER", user);
        insertLeg(conn, bad, inrA, "DEBIT", 500, "INR");

        assertCommitFailsAsUnbalanced();
    }

    // ---- row-level constraints -------------------------------------------------------

    @Test
    void zeroOrNegativeAmountIsRejectedImmediately() throws SQLException {
        UUID tx = insertTransaction(conn, "TRANSFER", user);

        assertThatThrownBy(() -> insertLeg(conn, tx, inrA, "DEBIT", 0, "INR"))
                .isInstanceOf(SQLException.class);
        conn.rollback();
    }

    private void assertCommitFailsAsUnbalanced() throws SQLException {
        assertThatThrownBy(conn::commit)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("unbalanced");

        conn.rollback(); // no-op after a failed commit; makes the connection reusable
        assertThat(count(conn, "SELECT count(*) FROM transactions")).isZero();
        assertThat(count(conn, "SELECT count(*) FROM ledger_entries")).isZero();
    }
}
