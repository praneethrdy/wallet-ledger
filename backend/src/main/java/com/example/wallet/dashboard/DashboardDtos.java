package com.example.wallet.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.example.wallet.ledger.TransactionDtos.TransactionSummary;

/**
 * Money "in" and "out" are measured per transaction across ALL the caller's wallets, so moving
 * money between your own wallets is neither inflow nor outflow.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record CurrencyTotal(String currency, long balance) {
    }

    /** In {@code currency} (the platform default currency). */
    public record Flow(String currency, long inflow, long outflow) {
    }

    public record DailyFlow(LocalDate date, long inflow, long outflow) {
    }

    public record DashboardSummary(List<CurrencyTotal> totals, Flow thisMonth, List<DailyFlow> cashFlow30d,
                                   List<TransactionSummary> recentTransactions) {
    }
}
