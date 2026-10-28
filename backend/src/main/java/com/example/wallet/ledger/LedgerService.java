package com.example.wallet.ledger;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.common.Money;
import com.example.wallet.common.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The only code that writes to {@code transactions} and {@code ledger_entries}.
 *
 * <p>Contract for callers:
 * <ul>
 *   <li>Call inside your own transaction ({@code MANDATORY}): the posting, your balance checks
 *       and your account locks must commit or roll back together.</li>
 *   <li>Lock the affected user wallets first ({@link AccountLocker}) and check balances
 *       under those locks. This class enforces double-entry, not business rules.</li>
 * </ul>
 *
 * <p>WHY plain JDBC here and not JPA entities: ledger rows are insert-only. A managed JPA
 * entity can be modified by accident and dirty-checking would then issue an UPDATE (rejected
 * by the append-only trigger, but only at flush time, far from the bug). With JDBC, the only
 * SQL this class can send is the INSERTs below.
 */
@Service
public class LedgerService {

    static final String SIGNED_AMOUNT = "CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LedgerService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PostedTransaction post(Posting posting) {
        assertBalanced(posting.legs());
        assertLegCurrenciesMatchAccounts(posting.legs());

        UUID id = UuidV7.generate(clock);
        // Postgres stores microseconds; truncate so the value we return equals what's stored.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String reference = TransactionReference.generate(now);

        jdbc.update("""
                INSERT INTO transactions (id, type, reference, description, reversal_of_transaction_id,
                                          initiated_by_user_id, idempotency_key_id, metadata, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                id, posting.type().name(), reference, posting.description(), posting.reversalOf(),
                posting.initiatedBy(), posting.idempotencyKeyId(), toJson(posting.metadata()), Timestamp.from(now));

        jdbc.batchUpdate("""
                INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, currency, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, posting.legs(), posting.legs().size(), (ps, leg) -> {
            ps.setObject(1, id);
            ps.setObject(2, leg.accountId());
            ps.setString(3, leg.direction().name());
            ps.setLong(4, leg.amount().amountMinor());
            ps.setString(5, leg.amount().currency());
            ps.setTimestamp(6, Timestamp.from(now));
        });

        return new PostedTransaction(id, reference, posting.type(), now, posting.legs());
    }

    /** Derived balance: SUM(credits) - SUM(debits). Under a row lock when used for a check. */
    public long balanceOf(UUID accountId) {
        Long balance = jdbc.queryForObject(
                "SELECT COALESCE(SUM(" + SIGNED_AMOUNT + "), 0) FROM ledger_entries WHERE account_id = ?",
                Long.class, accountId);
        return balance == null ? 0 : balance;
    }

    /** Balances for several accounts in one query; accounts without entries map to 0. */
    public Map<UUID, Long> balancesOf(Collection<UUID> accountIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (accountIds.isEmpty()) {
            return result;
        }
        accountIds.forEach(id -> result.put(id, 0L));
        jdbc.query("SELECT account_id, SUM(" + SIGNED_AMOUNT + ") FROM ledger_entries WHERE account_id IN ("
                        + Sql.placeholders(accountIds.size()) + ") GROUP BY account_id",
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getLong(2));
                },
                accountIds.toArray());
        return result;
    }

    /**
     * The Java half of the double-entry rule (the deferred DB trigger is the other half):
     * at least one DEBIT and one CREDIT, positive amounts, and per currency debits == credits.
     */
    static void assertBalanced(List<Posting.Leg> legs) {
        if (legs.isEmpty()) {
            throw new UnbalancedPostingException("A posting needs at least one leg");
        }
        boolean hasDebit = false;
        boolean hasCredit = false;
        Map<String, Money> net = new TreeMap<>();
        for (Posting.Leg leg : legs) {
            if (!leg.amount().isPositive()) {
                throw new UnbalancedPostingException("Leg amounts must be positive: " + leg);
            }
            hasDebit |= leg.direction() == Direction.DEBIT;
            hasCredit |= leg.direction() == Direction.CREDIT;
            Money signed = leg.direction() == Direction.CREDIT ? leg.amount() : leg.amount().negate();
            net.merge(leg.amount().currency(), signed, Money::plus);
        }
        if (!hasDebit || !hasCredit) {
            throw new UnbalancedPostingException("A posting needs at least one DEBIT and one CREDIT");
        }
        net.forEach((currency, sum) -> {
            if (!sum.isZero()) {
                throw new UnbalancedPostingException("Posting is unbalanced in " + currency + " by " + sum.amountMinor());
            }
        });
    }

    private void assertLegCurrenciesMatchAccounts(List<Posting.Leg> legs) {
        List<UUID> ids = legs.stream().map(Posting.Leg::accountId).distinct().toList();
        Map<UUID, String> currencies = new HashMap<>();
        jdbc.query("SELECT id, currency FROM accounts WHERE id IN (" + Sql.placeholders(ids.size()) + ")",
                rs -> {
                    currencies.put(rs.getObject(1, UUID.class), rs.getString(2));
                },
                ids.toArray());
        for (Posting.Leg leg : legs) {
            String accountCurrency = currencies.get(leg.accountId());
            if (accountCurrency == null) {
                throw new UnbalancedPostingException("Unknown account " + leg.accountId());
            }
            if (!accountCurrency.equals(leg.amount().currency())) {
                throw new UnbalancedPostingException("Leg currency " + leg.amount().currency()
                        + " does not match account currency " + accountCurrency);
            }
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Metadata is not serializable", e);
        }
    }
}
