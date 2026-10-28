package com.example.wallet.dev;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.common.UuidV7;
import com.example.wallet.user.Role;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;

/**
 * DEV PROFILE ONLY. Creates one user per role with well-known passwords (listed in the README),
 * because self-registration can only ever create CUSTOMERs. Idempotent: safe on every startup.
 * Wallets, deposits and sample transfers are added to the seed in later steps.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    record SeedUser(String email, String password, Role role) {
    }

    static final List<SeedUser> USERS = List.of(
            new SeedUser("admin@wallet.local", "admin-pass-123", Role.ADMIN),
            new SeedUser("support@wallet.local", "support-pass-123", Role.SUPPORT),
            new SeedUser("alice@wallet.local", "alice-pass-123", Role.CUSTOMER),
            new SeedUser("bob@wallet.local", "bob-pass-123", Role.CUSTOMER));

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevDataSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        int created = 0;
        for (SeedUser seed : USERS) {
            if (!users.existsByEmail(seed.email())) {
                users.save(new User(UuidV7.generate(clock), seed.email(),
                        passwordEncoder.encode(seed.password()), seed.role(), clock.instant()));
                created++;
            }
        }
        // Never log the passwords, even in dev: log habits leak into prod.
        log.info("Dev seed: {} user(s) created, {} already present", created, USERS.size() - created);
    }
}
