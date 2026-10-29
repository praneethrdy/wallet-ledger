package com.example.wallet.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.testsupport.ApiTest;

class AccountBootstrapIT extends ApiTest {

    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;
    @Autowired Clock clock;

    private AccountBootstrap bootstrap(BootstrapProperties.Login admin, BootstrapProperties.Login demo) {
        return new AccountBootstrap(new BootstrapProperties(admin, demo), userRepository, accountRepository,
                passwordEncoder, txManager, clock);
    }

    @Test
    void createsTheAdminAndADemoCustomerWithAWalletOnce() throws Exception {
        var admin = new BootstrapProperties.Login("  Owner@Example.com ", "a-long-admin-password");
        var demo = new BootstrapProperties.Login("demo@example.com", "demo-password-123");
        bootstrap(admin, demo).run(null);
        bootstrap(admin, demo).run(null); // a restart: nothing new

        assertThat(ledger.count("SELECT count(*) FROM users WHERE email = 'owner@example.com' AND role = 'ADMIN'"))
                .isEqualTo(1);
        assertThat(ledger.count("""
                SELECT count(*) FROM accounts a JOIN users u ON u.id = a.owner_user_id
                WHERE u.email = 'demo@example.com' AND u.role = 'CUSTOMER' AND a.name = 'Main'
                """)).isEqualTo(1);
        login("owner@example.com", "a-long-admin-password");
    }

    @Test
    void neverChangesAnExistingUsersPassword() throws Exception {
        bootstrap(new BootstrapProperties.Login("owner@example.com", "original-password-1"), null).run(null);
        bootstrap(new BootstrapProperties.Login("owner@example.com", "attacker-chosen-pw"), null).run(null);
        login("owner@example.com", "original-password-1");
    }

    @Test
    void skipsUnsetLoginsAndRefusesShortPasswords() {
        bootstrap(null, new BootstrapProperties.Login(" ", "")).run(null);
        assertThat(ledger.count("SELECT count(*) FROM users")).isZero();
        assertThatThrownBy(() -> bootstrap(new BootstrapProperties.Login("owner@example.com", "short"), null).run(null))
                .hasMessageContaining("at least 10");
    }

    private void login(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk());
    }
}
