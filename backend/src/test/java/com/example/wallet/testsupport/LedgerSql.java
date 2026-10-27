package com.example.wallet.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Raw-JDBC fixtures for schema-level tests. These deliberately bypass the Java services:
 * the point of the DB-level tests is to prove the database protects itself even when
 * application code is wrong or skipped.
 */
public final class LedgerSql {

    private LedgerSql() {
    }

    public static UUID insertUser(Connection conn, String role) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, 'x', ?)")) {
            ps.setObject(1, id);
            ps.setString(2, id + "@test.local");
            ps.setString(3, role);
            ps.executeUpdate();
        }
        return id;
    }

    public static UUID insertWallet(Connection conn, UUID ownerId, String currency) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO accounts (id, owner_user_id, type, currency, name)
                VALUES (?, ?, 'USER_WALLET', ?, 'test wallet')
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, ownerId);
            ps.setString(3, currency);
            ps.executeUpdate();
        }
        return id;
    }

    public static UUID insertTransaction(Connection conn, String type, UUID initiatedBy) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO transactions (id, type, reference, initiated_by_user_id)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setObject(1, id);
            ps.setString(2, type);
            ps.setString(3, "TXN-TEST-" + id.toString().substring(0, 8).toUpperCase());
            ps.setObject(4, initiatedBy);
            ps.executeUpdate();
        }
        return id;
    }

    public static void insertLeg(Connection conn, UUID transactionId, UUID accountId,
                                 String direction, long amount, String currency) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, transactionId);
            ps.setObject(2, accountId);
            ps.setString(3, direction);
            ps.setLong(4, amount);
            ps.setString(5, currency);
            ps.executeUpdate();
        }
    }

    public static long count(Connection conn, String sql) throws SQLException {
        try (var st = conn.createStatement(); var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
