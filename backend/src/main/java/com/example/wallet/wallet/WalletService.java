package com.example.wallet.wallet;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.AppProperties;
import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.common.web.KeysetCursor;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.ledger.AccountType;
import com.example.wallet.ledger.Direction;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.SystemAccounts;
import com.example.wallet.ledger.TransactionType;
import com.example.wallet.wallet.WalletDtos.BalancePoint;
import com.example.wallet.wallet.WalletDtos.CreateWalletRequest;
import com.example.wallet.wallet.WalletDtos.EntryResponse;
import com.example.wallet.wallet.WalletDtos.WalletResponse;

/**
 * The caller's own wallets. Every lookup is scoped by owner, so another user's wallet is
 * indistinguishable from a missing one (ADR-009).
 */
@Service
public class WalletService {

    private static final String SIGNED = "CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END";

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final SystemAccounts systemAccounts;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final AppProperties app;
    private final Clock clock;

    public WalletService(AccountRepository accounts, LedgerService ledger, SystemAccounts systemAccounts,
                         AuditService audit, JdbcTemplate jdbc, AppProperties app, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.systemAccounts = systemAccounts;
        this.audit = audit;
        this.jdbc = jdbc;
        this.app = app;
        this.clock = clock;
    }

    @Transactional
    public WalletResponse create(AuthUser caller, CreateWalletRequest request) {
        String currency = request.currency() == null ? app.defaultCurrency() : request.currency();
        systemAccounts.requireSupported(currency);
        Account wallet = accounts.save(Account.newWallet(
                UuidV7.generate(clock), caller.id(), request.name().trim(), currency, clock.instant()));
        audit.record(caller.id(), "WALLET_CREATED", "account", wallet.getId(), Map.of("currency", currency), null);
        return toResponse(wallet, 0);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> list(AuthUser caller) {
        List<Account> wallets = accounts.findByOwnerUserIdAndTypeOrderByCreatedAtAsc(caller.id(), AccountType.USER_WALLET);
        Map<UUID, Long> balances = ledger.balancesOf(wallets.stream().map(Account::getId).toList());
        return wallets.stream().map(w -> toResponse(w, balances.get(w.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(AuthUser caller, UUID walletId) {
        Account wallet = owned(caller, walletId);
        return toResponse(wallet, ledger.balanceOf(walletId));
    }

    /**
     * Newest first. Running balance = balance right after each entry. One indexed SUM gives the
     * balance at the newest row of the page; walking down the page (at most 100 rows)
     * subtracts each entry's effect. REPEATABLE READ: both queries see the same snapshot.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<EntryResponse> entries(AuthUser caller, UUID walletId, String cursor, int limit) {
        owned(caller, walletId);
        KeysetCursor after = KeysetCursor.decode(cursor);

        List<Object> args = new ArrayList<>(List.of(walletId));
        String cursorClause = "";
        if (after != null) {
            cursorClause = " AND (e.created_at, e.id) < (?, ?)";
            args.add(after.createdAtTimestamp());
            args.add(parseEntryId(after.id()));
        }
        args.add(limit + 1);

        List<EntryRow> rows = jdbc.query("""
                SELECT e.id, e.transaction_id, t.reference, t.type, t.description,
                       e.direction, e.amount, e.currency, e.created_at
                FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                WHERE e.account_id = ?""" + cursorClause + """

                ORDER BY e.created_at DESC, e.id DESC
                LIMIT ?
                """, (rs, n) -> new EntryRow(
                rs.getLong("id"),
                rs.getObject("transaction_id", UUID.class),
                rs.getString("reference"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("description"),
                Direction.valueOf(rs.getString("direction")),
                rs.getLong("amount"),
                rs.getString("currency"),
                rs.getTimestamp("created_at")), args.toArray());

        if (rows.isEmpty()) {
            return new PageResponse<>(List.of(), null);
        }
        EntryRow newest = rows.get(0);
        Long balanceAtNewest = jdbc.queryForObject(
                "SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM ledger_entries "
                        + "WHERE account_id = ? AND (created_at, id) <= (?, ?)",
                Long.class, walletId, newest.createdAt(), newest.id());

        long running = balanceAtNewest == null ? 0 : balanceAtNewest;
        List<EntryWithCursor> withBalances = new ArrayList<>();
        for (EntryRow row : rows) {
            withBalances.add(new EntryWithCursor(new EntryResponse(row.transactionId(), row.reference(), row.type(),
                    row.description(), row.direction(), row.amount(), row.currency(), running,
                    row.createdAt().toInstant()), row));
            running -= row.direction().signed(row.amount());
        }
        PageResponse<EntryWithCursor> page = PageResponse.of(withBalances, limit,
                e -> new KeysetCursor(e.row().createdAt().toInstant(), Long.toString(e.row().id())).encode());
        return new PageResponse<>(page.items().stream().map(EntryWithCursor::entry).toList(), page.nextCursor());
    }

    /** Closing balance per day for the last {@code days} days (including today), oldest first. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<BalancePoint> balanceHistory(AuthUser caller, UUID walletId, int days) {
        owned(caller, walletId);
        ZoneId zone = app.reportingZone();
        LocalDate lastDay = LocalDate.now(clock.withZone(zone));
        LocalDate firstDay = lastDay.minusDays(days - 1L);
        Timestamp start = Timestamp.from(firstDay.atStartOfDay(zone).toInstant());

        return jdbc.query("""
                WITH days AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day
                ),
                opening AS (
                    SELECT COALESCE(SUM(%1$s), 0) AS balance
                    FROM ledger_entries WHERE account_id = ? AND created_at < ?
                ),
                daily AS (
                    SELECT (created_at AT TIME ZONE ?)::date AS day, SUM(%1$s) AS net
                    FROM ledger_entries WHERE account_id = ? AND created_at >= ?
                    GROUP BY 1
                )
                SELECT d.day,
                       (SELECT balance FROM opening) + SUM(COALESCE(daily.net, 0)) OVER (ORDER BY d.day) AS closing
                FROM days d LEFT JOIN daily ON daily.day = d.day
                ORDER BY d.day
                """.formatted(SIGNED),
                (rs, n) -> new BalancePoint(rs.getObject("day", LocalDate.class), rs.getLong("closing")),
                firstDay, lastDay, walletId, start, zone.getId(), walletId, start);
    }

    private Account owned(AuthUser caller, UUID walletId) {
        return accounts.findByIdAndOwnerUserIdAndType(walletId, caller.id(), AccountType.USER_WALLET)
                .orElseThrow(() -> ApiException.notFound("Wallet"));
    }

    static WalletResponse toResponse(Account wallet, long balance) {
        return new WalletResponse(wallet.getId(), wallet.getName(), wallet.getCurrency(), wallet.getStatus(),
                balance, wallet.getCreatedAt());
    }

    private static long parseEntryId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }

    private record EntryRow(long id, UUID transactionId, String reference, TransactionType type, String description,
                            Direction direction, long amount, String currency, Timestamp createdAt) {
    }

    private record EntryWithCursor(EntryResponse entry, EntryRow row) {
    }
}
