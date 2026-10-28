package com.example.wallet.testsupport;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.wallet.auth.AuthDtos.LoginRequest;
import com.example.wallet.auth.AuthService;
import com.example.wallet.auth.ClientInfo;
import com.example.wallet.common.UuidV7;
import com.example.wallet.user.Role;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;

/**
 * Creates users of any role (self-registration only makes CUSTOMERs) and logs them in through
 * the real AuthService, so tests use genuine access tokens rather than mocked authentication.
 */
@Component
public class TestUsers {

    public static final String PASSWORD = "test-password-123";

    public record TestUser(UUID id, String email, Role role, String accessToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final Clock clock;
    private String passwordHash; // Argon2 is slow on purpose; hash the shared test password once

    public TestUsers(UserRepository users, PasswordEncoder passwordEncoder, AuthService authService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.clock = clock;
    }

    public TestUser create(Role role) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.local";
        User user = users.save(new User(UuidV7.generate(clock), email, passwordHash, role, clock.instant()));
        String token = authService.login(new LoginRequest(email, PASSWORD), new ClientInfo("127.0.0.1", "test"))
                .accessToken();
        return new TestUser(user.getId(), email, role, token);
    }
}
