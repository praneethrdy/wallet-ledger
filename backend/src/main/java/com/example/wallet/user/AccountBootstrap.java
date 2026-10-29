package com.example.wallet.user;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.UuidV7;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountRepository;

/**
 * Creates the accounts a fresh deployment needs, from the environment, once:
 * <ul>
 *   <li><b>Admin</b> ({@code BOOTSTRAP_ADMIN_EMAIL} / {@code BOOTSTRAP_ADMIN_PASSWORD}): the first
 *       staff login. Registration only ever creates customers, so without this a new deployment
 *       would have no way into the console.</li>
 *   <li><b>Demo customer</b> ({@code BOOTSTRAP_DEMO_EMAIL} / {@code BOOTSTRAP_DEMO_PASSWORD}), with
 *       an empty INR wallet: a public login for visitors who'd rather not register.</li>
 * </ul>
 * Existing users are never modified: changing a password here later does nothing, so a leaked
 * env var can't be used to reset the admin. Unset variables are simply skipped.
 */
@Component
public class AccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);

    private final BootstrapProperties props;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AccountBootstrap(BootstrapProperties props, UserRepository users, AccountRepository accounts,
                            PasswordEncoder passwordEncoder, PlatformTransactionManager txManager, Clock clock) {
        this.props = props;
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(s -> {
            create(props.admin(), Role.ADMIN, "admin");
            User demo = create(props.demo(), Role.CUSTOMER, "demo customer");
            if (demo != null) {
                accounts.save(Account.newWallet(UuidV7.generate(clock), demo.getId(), "Main", "INR", clock.instant()));
            }
        });
    }

    /** Returns the user if it was created now; null if skipped or already there. */
    private User create(BootstrapProperties.Login login, Role role, String what) {
        if (login == null || !login.set()) {
            return null;
        }
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return null;
        }
        if (login.password().length() < 10) {
            throw new IllegalStateException("Bootstrap " + what + " password must be at least 10 characters");
        }
        User user = users.save(new User(UuidV7.generate(clock), email, passwordEncoder.encode(login.password()), role,
                clock.instant()));
        log.info("Bootstrap: created the {} account", what); // never log the email or password
        return user;
    }
}
