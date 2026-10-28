package com.example.wallet.testsupport;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.Money;
import com.example.wallet.common.UuidV7;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.PostedTransaction;
import com.example.wallet.ledger.Posting;
import com.example.wallet.ledger.SystemAccountCode;
import com.example.wallet.ledger.SystemAccounts;
import com.example.wallet.ledger.TransactionType;
import com.example.wallet.testsupport.TestUsers.TestUser;

/**
 * Wallet and funding fixtures. Funding goes through the real LedgerService and creates a
 * SUCCEEDED deposit row, exactly like a settled deposit, so reconciliation stays clean.
 */
@Component
public class TestLedger {

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final SystemAccounts systemAccounts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TestLedger(AccountRepository accounts, LedgerService ledger, SystemAccounts systemAccounts,
                      JdbcTemplate jdbc, PlatformTransactionManager txManager, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.systemAccounts = systemAccounts;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public UUID wallet(TestUser owner) {
        return wallet(owner, "INR");
    }

    public UUID wallet(TestUser owner, String currency) {
        return accounts.save(Account.newWallet(UuidV7.generate(clock), owner.id(), "Test " + currency + " wallet",
                currency, clock.instant())).getId();
    }

    public UUID fundedWallet(TestUser owner, long amount) {
        UUID wallet = wallet(owner);
        fund(wallet, amount);
        return wallet;
    }

    /** A settled deposit: DEBIT EXTERNAL_FUNDING, CREDIT wallet, plus its deposits row. */
    public UUID fund(UUID walletId, long amount) {
        return tx.execute(status -> {
            Map<String, Object> row = jdbc.queryForMap("SELECT currency, owner_user_id FROM accounts WHERE id = ?", walletId);
            String currency = ((String) row.get("currency")).trim();
            UUID owner = (UUID) row.get("owner_user_id");
            Money money = Money.of(amount, currency);
            PostedTransaction posted = ledger.post(new Posting(TransactionType.DEPOSIT, "Test funding", owner, null, null,
                    Map.of(), List.of(
                            Posting.Leg.debit(systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, currency), money),
                            Posting.Leg.credit(walletId, money))));
            jdbc.update("""
                    INSERT INTO deposits (id, wallet_id, amount, currency, provider_reference, status, transaction_id)
                    VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?)
                    """, UuidV7.generate(clock), walletId, amount, currency, "test_" + UUID.randomUUID(), posted.id());
            return posted.id();
        });
    }

    public long balance(UUID walletId) {
        return ledger.balanceOf(walletId);
    }

    public void setStatus(UUID walletId, String status) {
        jdbc.update("UPDATE accounts SET status = ? WHERE id = ?", status, walletId);
    }

    /** debits - credits per currency across the whole ledger; every value must be 0. */
    public Map<String, Long> globalImbalance() {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT currency, SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END)
                FROM ledger_entries GROUP BY currency
                """, rs -> {
            result.put(rs.getString(1).trim(), rs.getLong(2));
        });
        return result;
    }

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
