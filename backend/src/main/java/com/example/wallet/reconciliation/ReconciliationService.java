package com.example.wallet.reconciliation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.common.web.KeysetCursor;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.reconciliation.ReconciliationDtos.Finding;
import com.example.wallet.reconciliation.ReconciliationDtos.RunResponse;
import com.example.wallet.reconciliation.ReconciliationDtos.Severity;
import com.example.wallet.reconciliation.ReconciliationDtos.Trigger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Independently re-checks the ledger's invariants from the raw rows. The app and the DB
 * triggers enforce them on write; this catches anything that slipped through anyway (a bug, a
 * manual SQL fix with triggers disabled, a restored backup).
 *
 * <ol>
 *   <li>GLOBAL_BALANCE: per currency, all debits == all credits</li>
 *   <li>TRANSACTION_UNBALANCED / TRANSACTION_MISSING_SIDE: every transaction balances</li>
 *   <li>NEGATIVE_WALLET: no user wallet below zero</li>
 *   <li>Cached balances: not applicable, no cached balance exists (ADR-011)</li>
 *   <li>DEPOSIT_*: each SUCCEEDED deposit has exactly one matching DEPOSIT transaction and vice
 *       versa; PENDING older than 24h is a WARNING</li>
 *   <li>REVERSED_TWICE / REVERSAL_OF_REVERSAL</li>
 * </ol>
 *
 * The checks run in one REPEATABLE READ, read-only transaction: they all see the same
 * snapshot, so a transfer committing mid-run can't make check 1 and check 2 disagree.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final int MAX_ROWS_PER_CHECK = 100;
    private static final String SIGNED = "CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate snapshotTx;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Timer duration;
    private final Counter mismatches;

    public ReconciliationService(JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper objectMapper,
                                 Clock clock, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.snapshotTx = new TransactionTemplate(txManager);
        this.snapshotTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.snapshotTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshotTx.setReadOnly(true);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.duration = Timer.builder("ledger.reconciliation.duration").register(registry);
        this.mismatches = Counter.builder("ledger.reconciliation.mismatches")
                .description("Reconciliation ERROR findings").register(registry);
    }

    private record Snapshot(long totalDebits, long totalCredits, long checkedTransactions, List<Finding> findings) {
    }

    public RunResponse run(Trigger trigger) {
        UUID runId = UuidV7.generate(clock);
        Instant started = clock.instant();
        writeTx.executeWithoutResult(s -> jdbc.update("""
                INSERT INTO reconciliation_runs (id, trigger_type, started_at, status) VALUES (?, ?, ?, 'RUNNING')
                """, runId, trigger.name(), Timestamp.from(started)));

        Snapshot snapshot;
        try {
            snapshot = duration.recordCallable(() -> snapshotTx.execute(s -> check()));
        } catch (Exception e) {
            log.error("Reconciliation run {} crashed", runId, e);
            finish(runId, "FAILED", null, List.of(new Finding("RUN_ERROR", Severity.ERROR,
                    Map.of("error", e.getClass().getSimpleName()))));
            mismatches.increment();
            return get(runId);
        }

        long errors = snapshot.findings().stream().filter(f -> f.severity() == Severity.ERROR).count();
        String status = errors == 0 ? "PASSED" : "FAILED";
        finish(runId, status, snapshot, snapshot.findings());
        if (errors > 0) {
            mismatches.increment(errors);
            log.error("Reconciliation run {} FAILED with {} error finding(s): {}", runId, errors, snapshot.findings());
        } else {
            log.info("Reconciliation run {} PASSED ({} transactions, {} warning(s))", runId,
                    snapshot.checkedTransactions(), snapshot.findings().size());
        }
        return get(runId);
    }

    public RunResponse get(UUID runId) {
        List<RunResponse> runs = jdbc.query("SELECT * , mismatches::text AS findings FROM reconciliation_runs WHERE id = ?",
                (rs, n) -> map(rs), runId);
        if (runs.isEmpty()) {
            throw ApiException.notFound("Reconciliation run");
        }
        return runs.get(0);
    }

    public PageResponse<RunResponse> list(String cursor, int limit) {
        List<Object> args = new ArrayList<>();
        String where = "";
        KeysetCursor after = KeysetCursor.decode(cursor);
        if (after != null) {
            where = " WHERE (started_at, id) < (?, ?)";
            args.add(after.createdAtTimestamp());
            try {
                args.add(UUID.fromString(after.id()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
            }
        }
        args.add(limit + 1);
        List<RunResponse> runs = jdbc.query("SELECT *, mismatches::text AS findings FROM reconciliation_runs" + where
                + " ORDER BY started_at DESC, id DESC LIMIT ?", (rs, n) -> map(rs), args.toArray());
        return PageResponse.of(runs, limit, r -> new KeysetCursor(r.startedAt(), r.id().toString()).encode());
    }

    // ---- the checks ----------------------------------------------------------------------

    private Snapshot check() {
        List<Finding> findings = new ArrayList<>();
        long[] totals = new long[2];

        // 1. Global debits == credits, per currency.
        jdbc.query("""
                SELECT currency,
                       COALESCE(SUM(amount) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
                       COALESCE(SUM(amount) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
                FROM ledger_entries GROUP BY currency ORDER BY currency
                """, rs -> {
            long debits = rs.getLong("debits");
            long credits = rs.getLong("credits");
            totals[0] += debits;
            totals[1] += credits;
            if (debits != credits) {
                findings.add(error("GLOBAL_BALANCE", "currency", rs.getString("currency").trim(),
                        "debits", debits, "credits", credits));
            }
        });

        // 2. Each transaction balances per currency, and has both sides.
        jdbc.query("SELECT transaction_id, currency, SUM(" + SIGNED + ") AS net FROM ledger_entries "
                        + "GROUP BY transaction_id, currency HAVING SUM(" + SIGNED + ") <> 0 LIMIT " + MAX_ROWS_PER_CHECK,
                rs -> {
                    findings.add(error("TRANSACTION_UNBALANCED", "transactionId", rs.getObject("transaction_id", UUID.class),
                            "currency", rs.getString("currency").trim(), "creditsMinusDebits", rs.getLong("net")));
                });
        jdbc.query("""
                SELECT t.id FROM transactions t
                WHERE NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transaction_id = t.id AND e.direction = 'DEBIT')
                   OR NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transaction_id = t.id AND e.direction = 'CREDIT')
                LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("TRANSACTION_MISSING_SIDE", "transactionId", rs.getObject("id", UUID.class)));
        });

        // 3. No user wallet is negative.
        jdbc.query("""
                SELECT a.id, SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) AS balance
                FROM accounts a JOIN ledger_entries e ON e.account_id = a.id
                WHERE a.type = 'USER_WALLET'
                GROUP BY a.id
                HAVING SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) < 0
                LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("NEGATIVE_WALLET", "walletId", rs.getObject("id", UUID.class), "balance", rs.getLong("balance")));
        });

        // 4. Cached balances: none exist (balances are always derived, ADR-011), nothing to compare.

        // 5. Deposits <-> DEPOSIT transactions, one-to-one and matching.
        jdbc.query("""
                SELECT d.id FROM deposits d
                WHERE d.status = 'SUCCEEDED' AND NOT EXISTS (
                    SELECT 1 FROM transactions t JOIN ledger_entries e ON e.transaction_id = t.id
                    WHERE t.id = d.transaction_id AND t.type = 'DEPOSIT'
                      AND e.account_id = d.wallet_id AND e.direction = 'CREDIT'
                      AND e.amount = d.amount AND e.currency = d.currency)
                LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("DEPOSIT_WITHOUT_MATCHING_TRANSACTION", "depositId", rs.getObject("id", UUID.class)));
        });
        jdbc.query("""
                SELECT t.id FROM transactions t
                WHERE t.type = 'DEPOSIT' AND NOT EXISTS (SELECT 1 FROM deposits d WHERE d.transaction_id = t.id)
                LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("TRANSACTION_WITHOUT_DEPOSIT", "transactionId", rs.getObject("id", UUID.class)));
        });
        jdbc.query("""
                SELECT d.id, d.created_at FROM deposits d
                WHERE d.status = 'PENDING' AND d.created_at < ?
                ORDER BY d.created_at LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(new Finding("STALE_PENDING_DEPOSIT", Severity.WARNING, details(
                    "depositId", rs.getObject("id", UUID.class),
                    "createdAt", rs.getTimestamp("created_at").toInstant().toString())));
        }, Timestamp.from(clock.instant().minus(Duration.ofHours(24))));

        // 6. Reversals: at most one per transaction, and never of a reversal.
        jdbc.query("""
                SELECT reversal_of_transaction_id, count(*) AS n FROM transactions
                WHERE reversal_of_transaction_id IS NOT NULL
                GROUP BY reversal_of_transaction_id HAVING count(*) > 1 LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("REVERSED_TWICE", "transactionId", rs.getObject("reversal_of_transaction_id", UUID.class),
                    "reversals", rs.getLong("n")));
        });
        jdbc.query("""
                SELECT r.id FROM transactions r JOIN transactions o ON o.id = r.reversal_of_transaction_id
                WHERE o.type = 'REVERSAL' LIMIT %d
                """.formatted(MAX_ROWS_PER_CHECK), rs -> {
            findings.add(error("REVERSAL_OF_REVERSAL", "transactionId", rs.getObject("id", UUID.class)));
        });

        Long checked = jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class);
        return new Snapshot(totals[0], totals[1], checked == null ? 0 : checked, findings);
    }

    // ---- persistence ---------------------------------------------------------------------

    private void finish(UUID runId, String status, Snapshot snapshot, List<Finding> findings) {
        writeTx.executeWithoutResult(s -> jdbc.update("""
                UPDATE reconciliation_runs
                SET finished_at = ?, status = ?, total_debits = ?, total_credits = ?, checked_transactions = ?,
                    mismatches = ?::jsonb
                WHERE id = ?
                """, Timestamp.from(clock.instant()), status,
                snapshot == null ? null : snapshot.totalDebits(),
                snapshot == null ? null : snapshot.totalCredits(),
                snapshot == null ? null : snapshot.checkedTransactions(),
                toJson(findings), runId));
    }

    private RunResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new RunResponse(
                rs.getObject("id", UUID.class),
                Trigger.valueOf(rs.getString("trigger_type")),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                finished == null ? null : finished.toInstant(),
                (Long) rs.getObject("total_debits"),
                (Long) rs.getObject("total_credits"),
                (Long) rs.getObject("checked_transactions"),
                fromJson(rs.getString("findings")));
    }

    private String toJson(List<Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Finding> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Finding>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable reconciliation findings", e);
        }
    }

    private static Finding error(String check, Object... keyValues) {
        return new Finding(check, Severity.ERROR, details(keyValues));
    }

    private static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            details.put((String) keyValues[i], value instanceof UUID ? value.toString() : value);
        }
        return details;
    }
}
