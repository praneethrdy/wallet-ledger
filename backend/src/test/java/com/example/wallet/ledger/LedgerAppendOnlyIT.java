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
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.wallet.testsupport.AbstractIntegrationTest;

/**
 * Spec §9 test 8: ledger tables reject UPDATE and DELETE at the database level.
 *
 * <p>We use the schema OWNER connection for the mutation attempts: the owner has every table
 * privilege, so the only thing that can stop it is your trigger. (The app role is additionally
 * blocked by REVOKE — see SchemaIT and the last test here.)
 */
class LedgerAppendOnlyIT extends AbstractIntegrationTest {

    private UUID txId;

    @BeforeEach
    void postOneBalancedTransaction() throws SQLException {
        try (Connection conn = appConnection()) {
            UUID user = insertUser(conn, "CUSTOMER");
            UUID a = insertWallet(conn, user, "INR");
            UUID b = insertWallet(conn, user, "INR");

            conn.setAutoCommit(false);
            txId = insertTransaction(conn, "TRANSFER", user);
            insertLeg(conn, txId, a, "DEBIT", 10_000, "INR");
            insertLeg(conn, txId, b, "CREDIT", 10_000, "INR");
            conn.commit();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE ledger_entries SET amount = 1",
            "UPDATE ledger_entries SET direction = 'CREDIT' WHERE direction = 'DEBIT'",
            "DELETE FROM ledger_entries",
            "UPDATE transactions SET description = 'rewritten history'",
            "DELETE FROM transactions"
    })
    void ownerCannotMutateLedgerRows(String sql) throws SQLException {
        try (Connection conn = ownerConnection(); Statement st = conn.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate(sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("append-only");
        }
        assertLedgerUnchanged();
    }

    @Test
    void appRoleCannotMutateLedgerRowsEither() throws SQLException {
        try (Connection conn = appConnection(); Statement st = conn.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("UPDATE ledger_entries SET amount = 1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.executeUpdate("DELETE FROM transactions"))
                    .isInstanceOf(SQLException.class);
        }
        assertLedgerUnchanged();
    }

    private void assertLedgerUnchanged() throws SQLException {
        try (Connection conn = ownerConnection()) {
            assertThat(count(conn, "SELECT count(*) FROM transactions")).isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM ledger_entries")).isEqualTo(2);
            assertThat(count(conn, "SELECT sum(amount) FROM ledger_entries")).isEqualTo(20_000);
            assertThat(count(conn, "SELECT count(*) FROM transactions WHERE description IS NOT NULL")).isZero();
        }
    }
}
