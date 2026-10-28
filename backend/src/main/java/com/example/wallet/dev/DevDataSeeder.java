package com.example.wallet.dev;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.AppProperties;
import com.example.wallet.common.Money;
import com.example.wallet.common.UuidV7;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.ledger.AccountType;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.PostedTransaction;
import com.example.wallet.ledger.Posting;
import com.example.wallet.ledger.SystemAccountCode;
import com.example.wallet.ledger.SystemAccounts;
import com.example.wallet.ledger.TransactionType;
import com.example.wallet.user.Role;
import com.example.wallet.user.User;
import com.example.wallet.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DEV PROFILE ONLY. Seeds a usable demo world on startup (idempotent):
 * <ul>
 *   <li>one user per role, with the passwords listed in the README</li>
 *   <li>two INR wallets per customer, funded by real double-entry deposits 30 days ago</li>
 *   <li>~30 days of transfers between them, so dashboards and sparklines have data</li>
 * </ul>
 * Everything goes through LedgerService, so the seed passes reconciliation like real data.
 * To place history in the past, the seeder gives LedgerService a fixed Clock per posting;
 * production code has no way to backdate a posting.
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

    private static final String[] NOTES = {"Dinner", "Rent share", "Groceries", "Movie tickets", "Cab", "Coffee",
            "Birthday gift", "Electricity bill", "Savings top-up", "Weekend trip"};

    private final UserRepository users;
    private final AccountRepository accounts;
    private final SystemAccounts systemAccounts;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final AppProperties app;
    private final Clock clock;

    public DevDataSeeder(UserRepository users, AccountRepository accounts, SystemAccounts systemAccounts,
                         PasswordEncoder passwordEncoder, JdbcTemplate jdbc, ObjectMapper objectMapper,
                         PlatformTransactionManager txManager, AppProperties app, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.systemAccounts = systemAccounts;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(txManager);
        this.app = app;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        tx.executeWithoutResult(status -> {
            seedUsers();
            seedLedger();
        });
    }

    private void seedUsers() {
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

    private void seedLedger() {
        User alice = users.findByEmail("alice@wallet.local").orElseThrow();
        User bob = users.findByEmail("bob@wallet.local").orElseThrow();
        if (!accounts.findByOwnerUserIdAndTypeOrderByCreatedAtAsc(alice.getId(), AccountType.USER_WALLET).isEmpty()) {
            log.info("Dev seed: wallets already present, skipping ledger seed");
            return;
        }

        ZoneId zone = app.reportingZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstDay = today.minusDays(30);
        Instant now = clock.instant();
        Instant start = firstDay.atTime(9, 0).atZone(zone).toInstant();

        List<Account> wallets = List.of(
                wallet(alice, "Main", start), wallet(alice, "Savings", start),
                wallet(bob, "Main", start), wallet(bob, "Travel", start));
        long[] opening = {500_000, 200_000, 400_000, 150_000}; // ₹5,000 / ₹2,000 / ₹4,000 / ₹1,500

        Map<UUID, Long> balances = new HashMap<>();
        for (int i = 0; i < wallets.size(); i++) {
            deposit(wallets.get(i), opening[i], start.plusSeconds(60L * i));
            balances.put(wallets.get(i).getId(), opening[i]);
        }

        Random random = new Random(42); // deterministic: the same demo data every time
        int transfers = 0;
        for (LocalDate day = firstDay.plusDays(1); !day.isAfter(today); day = day.plusDays(1)) {
            int perDay = 1 + random.nextInt(2);
            for (int k = 0; k < perDay; k++) {
                Instant at = day.atTime(10 + random.nextInt(10), random.nextInt(60)).atZone(zone).toInstant();
                if (at.isAfter(now)) {
                    at = now.minusSeconds(60L * (perDay - k)); // today: keep history in the past
                }
                // Pick a valid pair: different wallets, and never overdraw (same rule as the real API).
                for (int attempt = 0; attempt < 20; attempt++) {
                    Account from = wallets.get(random.nextInt(wallets.size()));
                    Account to = wallets.get(random.nextInt(wallets.size()));
                    long amount = (10L + random.nextInt(491)) * 100; // ₹10 - ₹500
                    if (from.equals(to) || balances.get(from.getId()) < amount) {
                        continue;
                    }
                    Money money = Money.of(amount, "INR");
                    ledgerAt(at).post(new Posting(TransactionType.TRANSFER, NOTES[random.nextInt(NOTES.length)],
                            from.getOwnerUserId(), null, null, Map.of(),
                            List.of(Posting.Leg.debit(from.getId(), money), Posting.Leg.credit(to.getId(), money))));
                    balances.merge(from.getId(), -amount, Long::sum);
                    balances.merge(to.getId(), amount, Long::sum);
                    transfers++;
                    break;
                }
            }
        }
        log.info("Dev seed: 4 wallets, 4 deposits and {} transfers over 30 days", transfers);
    }

    private Account wallet(User owner, String name, Instant createdAt) {
        // Flush now: LedgerService reads accounts via JDBC, which can't see unflushed JPA inserts.
        return accounts.saveAndFlush(Account.newWallet(UuidV7.generate(clock), owner.getId(), name, "INR", createdAt));
    }

    private void deposit(Account wallet, long amount, Instant at) {
        Money money = Money.of(amount, "INR");
        PostedTransaction posted = ledgerAt(at).post(new Posting(TransactionType.DEPOSIT, "Opening deposit",
                wallet.getOwnerUserId(), null, null, Map.of("seed", true),
                List.of(Posting.Leg.debit(systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, "INR"), money),
                        Posting.Leg.credit(wallet.getId(), money))));
        jdbc.update("""
                INSERT INTO deposits (id, wallet_id, amount, currency, provider_reference, status, transaction_id,
                                      created_at, updated_at)
                VALUES (?, ?, ?, 'INR', ?, 'SUCCEEDED', ?, ?, ?)
                """, UuidV7.generate(clock), wallet.getId(), amount, "seed_" + UUID.randomUUID(), posted.id(),
                Timestamp.from(at), Timestamp.from(at));
    }

    /** A LedgerService whose clock is pinned to {@code at}, used only to place seed history in the past. */
    private LedgerService ledgerAt(Instant at) {
        return new LedgerService(jdbc, objectMapper, Clock.fixed(at, ZoneOffset.UTC));
    }
}
