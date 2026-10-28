package com.example.wallet.dashboard;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.AppProperties;
import com.example.wallet.dashboard.DashboardDtos.CurrencyTotal;
import com.example.wallet.dashboard.DashboardDtos.DailyFlow;
import com.example.wallet.dashboard.DashboardDtos.DashboardSummary;
import com.example.wallet.dashboard.DashboardDtos.Flow;
import com.example.wallet.ledger.TransactionDtos.TransactionFilter;
import com.example.wallet.ledger.TransactionQueryService;

/**
 * Dashboard numbers, all computed by SQL aggregates (never by loading entries into Java).
 * REPEATABLE READ: every figure on the page comes from the same snapshot, so the totals and
 * the cash-flow chart can't disagree because a transfer landed between two queries.
 */
@Service
public class DashboardService {

    /** Net effect per transaction on the caller's wallets in one currency since a point in time. */
    private static final String NETS = """
            WITH nets AS (
                SELECT e.transaction_id, MIN(e.created_at) AS created_at,
                       SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END) AS net
                FROM ledger_entries e JOIN accounts a ON a.id = e.account_id
                WHERE a.owner_user_id = ? AND a.type = 'USER_WALLET' AND e.currency = ? AND e.created_at >= ?
                GROUP BY e.transaction_id
            )
            """;

    private final JdbcTemplate jdbc;
    private final TransactionQueryService transactions;
    private final AppProperties app;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, TransactionQueryService transactions, AppProperties app, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.app = app;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardSummary summary(AuthUser caller) {
        ZoneId zone = app.reportingZone();
        String currency = app.defaultCurrency();
        LocalDate today = LocalDate.now(clock.withZone(zone));

        List<CurrencyTotal> totals = jdbc.query("""
                SELECT a.currency, COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) AS balance
                FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
                WHERE a.owner_user_id = ? AND a.type = 'USER_WALLET'
                GROUP BY a.currency ORDER BY a.currency
                """, (rs, n) -> new CurrencyTotal(rs.getString("currency").trim(), rs.getLong("balance")), caller.id());

        Timestamp monthStart = Timestamp.from(today.withDayOfMonth(1).atStartOfDay(zone).toInstant());
        Flow thisMonth = jdbc.queryForObject(NETS + """
                SELECT COALESCE(SUM(net) FILTER (WHERE net > 0), 0) AS inflow,
                       COALESCE(SUM(-net) FILTER (WHERE net < 0), 0) AS outflow
                FROM nets
                """, (rs, n) -> new Flow(currency, rs.getLong("inflow"), rs.getLong("outflow")),
                caller.id(), currency, monthStart);

        LocalDate firstDay = today.minusDays(29);
        Timestamp windowStart = Timestamp.from(firstDay.atStartOfDay(zone).toInstant());
        List<DailyFlow> cashFlow = jdbc.query(NETS + """
                , days AS (SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day),
                daily AS (
                    SELECT (created_at AT TIME ZONE ?)::date AS day,
                           SUM(net) FILTER (WHERE net > 0) AS inflow,
                           SUM(-net) FILTER (WHERE net < 0) AS outflow
                    FROM nets GROUP BY 1
                )
                SELECT d.day, COALESCE(daily.inflow, 0) AS inflow, COALESCE(daily.outflow, 0) AS outflow
                FROM days d LEFT JOIN daily ON daily.day = d.day
                ORDER BY d.day
                """, (rs, n) -> new DailyFlow(rs.getObject("day", LocalDate.class), rs.getLong("inflow"), rs.getLong("outflow")),
                caller.id(), currency, windowStart, firstDay, today, zone.getId());

        var recent = transactions.listForCaller(caller, new TransactionFilter(null, null, null, null, null), null, 8).items();
        return new DashboardSummary(totals, thisMonth, cashFlow, recent);
    }
}
