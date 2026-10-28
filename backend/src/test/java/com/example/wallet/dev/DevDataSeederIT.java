package com.example.wallet.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.wallet.auth.AuthDtos.LoginRequest;
import com.example.wallet.auth.AuthService;
import com.example.wallet.auth.ClientInfo;
import com.example.wallet.reconciliation.ReconciliationDtos.Trigger;
import com.example.wallet.reconciliation.ReconciliationService;
import com.example.wallet.testsupport.AbstractIntegrationTest;

/** The dev seed: users per role, funded wallets, 30 days of history, and it all reconciles. */
@ActiveProfiles({"test", "dev"})
class DevDataSeederIT extends AbstractIntegrationTest {

    @Autowired
    DevDataSeeder seeder;

    @Autowired
    AuthService authService;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsUsersWalletsAndHistoryIdempotently() {
        // cleanDatabase() ran after the startup seed, so seed explicitly; twice to prove idempotency.
        seeder.seed();
        seeder.seed();

        assertThat(jdbc.queryForList("SELECT role FROM users ORDER BY role", String.class))
                .containsExactly("ADMIN", "CUSTOMER", "CUSTOMER", "SUPPORT");
        assertThat(count("SELECT count(*) FROM accounts WHERE type = 'USER_WALLET'")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM deposits WHERE status = 'SUCCEEDED'")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM transactions WHERE type = 'TRANSFER'")).isGreaterThanOrEqualTo(30);
        // Every one of the last 30 days has activity, so sparklines and cash-flow charts have data.
        assertThat(count("""
                SELECT count(DISTINCT (created_at AT TIME ZONE 'Asia/Kolkata')::date) FROM transactions
                WHERE type = 'TRANSFER'
                """)).isEqualTo(30);
    }

    @Test
    void seededDataPassesReconciliation() {
        seeder.seed();

        var run = reconciliation.run(Trigger.MANUAL);
        assertThat(run.status()).isEqualTo("PASSED");
        assertThat(run.findings()).isEmpty();
    }

    @Test
    void seededCredentialsCanLogIn() {
        seeder.seed();

        for (DevDataSeeder.SeedUser user : DevDataSeeder.USERS) {
            var tokens = authService.login(new LoginRequest(user.email(), user.password()),
                    new ClientInfo("127.0.0.1", "test"));
            assertThat(tokens.user().role()).isEqualTo(user.role());
        }
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
