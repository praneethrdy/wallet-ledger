package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.wallet.testsupport.AbstractIntegrationTest;

/** Migrations apply cleanly, and the app's DB role has exactly the privileges it needs. */
class SchemaIT extends AbstractIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc; // connected as wallet_app

    @Test
    void allMigrationsAreApplied() {
        var all = flyway.info().all();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion()).isEqualTo(all[all.length - 1].getVersion());
    }

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

        assertThat(tables).contains(
                "users", "refresh_tokens", "accounts", "idempotency_keys", "webhook_events",
                "reconciliation_runs", "audit_log", "shedlock",
                "transactions", "ledger_entries");
    }

    @Test
    void appRunsAsLeastPrivilegeRole() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
        assertThat(jdbc.queryForObject(
                "SELECT rolsuper OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname = current_user",
                Boolean.class)).isFalse();
    }

    @Test
    void appRoleCannotRunDdl() {
        assertThatThrownBy(() -> jdbc.execute("CREATE TABLE should_not_exist (id int)"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.execute("DROP TABLE users"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("must be owner");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transactions", "ledger_entries"})
    void appRoleCanOnlySelectAndInsertLedgerTables(String table) {
        assertThat(privilege(table, "SELECT")).as("SELECT on %s", table).isTrue();
        assertThat(privilege(table, "INSERT")).as("INSERT on %s", table).isTrue();
        assertThat(privilege(table, "UPDATE")).as("UPDATE on %s", table).isFalse();
        assertThat(privilege(table, "DELETE")).as("DELETE on %s", table).isFalse();
        assertThat(privilege(table, "TRUNCATE")).as("TRUNCATE on %s", table).isFalse();
    }

    @Test
    void appRoleCanUseLedgerEntriesIdSequence() {
        Boolean usage = jdbc.queryForObject(
                "SELECT has_sequence_privilege(current_user, pg_get_serial_sequence('ledger_entries', 'id'), 'USAGE')",
                Boolean.class);
        assertThat(usage).isTrue();
    }

    private boolean privilege(String table, String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, ?, ?)", Boolean.class, table, privilege));
    }
}
