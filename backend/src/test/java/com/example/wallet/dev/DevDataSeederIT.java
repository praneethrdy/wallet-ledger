package com.example.wallet.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.wallet.auth.AuthDtos.LoginRequest;
import com.example.wallet.auth.AuthService;
import com.example.wallet.auth.ClientInfo;
import com.example.wallet.testsupport.AbstractIntegrationTest;

/** The dev seed creates one user per role, is idempotent, and the README credentials work. */
@ActiveProfiles({"test", "dev"})
class DevDataSeederIT extends AbstractIntegrationTest {

    @Autowired
    DevDataSeeder seeder;

    @Autowired
    AuthService authService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsOneUserPerRoleAndIsIdempotent() {
        // cleanDatabase() ran after the startup seed, so seed explicitly; twice to prove idempotency.
        seeder.seed();
        seeder.seed();

        assertThat(jdbc.queryForList("SELECT role FROM users ORDER BY role", String.class))
                .containsExactly("ADMIN", "CUSTOMER", "CUSTOMER", "SUPPORT");
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
}
