package com.example.wallet.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.admin.AdminDtos.AdminWalletView;
import com.example.wallet.admin.AdminDtos.CurrencyAmount;
import com.example.wallet.admin.AdminDtos.LastReconciliation;
import com.example.wallet.admin.AdminDtos.Overview;
import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.AppProperties;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.web.KeysetCursor;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountLocker;
import com.example.wallet.ledger.AccountStatus;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.Sql;

/** Wallet search, freeze/unfreeze and the operations overview. */
@Service
public class AdminService {

    private final JdbcTemplate jdbc;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final AuditService audit;
    private final AppProperties app;
    private final Clock clock;

    public AdminService(JdbcTemplate jdbc, AccountLocker locker, LedgerService ledger, AuditService audit,
                        AppProperties app, Clock clock) {
        this.jdbc = jdbc;
        this.locker = locker;
        this.ledger = ledger;
        this.audit = audit;
        this.app = app;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminWalletView> searchWallets(String email, AccountStatus status, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.name, a.currency, a.status, a.owner_user_id, u.email::text AS email, a.created_at
                FROM accounts a JOIN users u ON u.id = a.owner_user_id
                WHERE a.type = 'USER_WALLET'
                """);
        List<Object> args = new ArrayList<>();
        if (email != null && !email.isBlank()) {
            sql.append(" AND u.email::text ILIKE ? ESCAPE '\\'");
            args.add(Sql.likeContains(email.trim().toLowerCase()));
        }
        if (status != null) {
            sql.append(" AND a.status = ?");
            args.add(status.name());
        }
        KeysetCursor after = KeysetCursor.decode(cursor);
        if (after != null) {
            sql.append(" AND (a.created_at, a.id) < (?, ?)");
            args.add(after.createdAtTimestamp());
            args.add(parseUuid(after.id()));
        }
        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT ?");
        args.add(limit + 1);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        Map<UUID, Long> balances = ledger.balancesOf(rows.stream().map(r -> (UUID) r.get("id")).toList());
        List<AdminWalletView> views = rows.stream().map(r -> new AdminWalletView(
                (UUID) r.get("id"),
                (String) r.get("name"),
                ((String) r.get("currency")).trim(),
                AccountStatus.valueOf((String) r.get("status")),
                balances.get((UUID) r.get("id")),
                (UUID) r.get("owner_user_id"),
                (String) r.get("email"),
                ((Timestamp) r.get("created_at")).toInstant())).toList();
        return PageResponse.of(views, limit, v -> new KeysetCursor(v.createdAt(), v.id().toString()).encode());
    }

    /** Idempotent by nature: freezing a frozen wallet is a no-op (and not re-audited). */
    @Transactional
    public AdminWalletView setFrozen(AuthUser admin, UUID walletId, boolean frozen) {
        Account wallet = locker.lockInOrder(List.of(walletId)).get(walletId);
        if (wallet == null || !wallet.isUserWallet()) {
            throw ApiException.notFound("Wallet");
        }
        AccountStatus before = wallet.getStatus();
        AccountStatus target = frozen ? AccountStatus.FROZEN : AccountStatus.ACTIVE;
        if (before != target) {
            if (frozen) {
                wallet.freeze();
            } else {
                wallet.unfreeze();
            }
            audit.record(admin.id(), frozen ? "WALLET_FROZEN" : "WALLET_UNFROZEN", "account", walletId,
                    Map.of("previousStatus", before.name()), null);
        }
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, wallet.getOwnerUserId());
        return new AdminWalletView(wallet.getId(), wallet.getName(), wallet.getCurrency(), target,
                ledger.balanceOf(walletId), wallet.getOwnerUserId(), email, wallet.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Instant now = clock.instant();
        Timestamp startOfToday = Timestamp.from(LocalDate.now(clock.withZone(app.reportingZone()))
                .atStartOfDay(app.reportingZone()).toInstant());

        List<CurrencyAmount> volume = jdbc.query("""
                SELECT e.currency, SUM(e.amount) AS amount
                FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                WHERE t.type = 'TRANSFER' AND e.direction = 'DEBIT' AND t.created_at >= ?
                GROUP BY e.currency ORDER BY e.currency
                """, (rs, n) -> new CurrencyAmount(rs.getString("currency").trim(), rs.getLong("amount")), startOfToday);
        Long transfers = jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE type = 'TRANSFER' AND created_at >= ?", Long.class, startOfToday);
        Long failedWebhooks = jdbc.queryForObject(
                "SELECT count(*) FROM webhook_events WHERE status IN ('FAILED', 'REJECTED') AND received_at >= ?",
                Long.class, Timestamp.from(now.minus(Duration.ofHours(24))));
        List<LastReconciliation> last = jdbc.query("""
                SELECT id, status, started_at, finished_at FROM reconciliation_runs
                ORDER BY started_at DESC LIMIT 1
                """, (rs, n) -> new LastReconciliation(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant()));

        return new Overview(volume, transfers == null ? 0 : transfers, failedWebhooks == null ? 0 : failedWebhooks,
                last.isEmpty() ? null : last.get(0));
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(com.example.wallet.common.error.ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
}
