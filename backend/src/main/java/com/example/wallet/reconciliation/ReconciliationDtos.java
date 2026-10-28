package com.example.wallet.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReconciliationDtos {

    private ReconciliationDtos() {
    }

    public enum Trigger { SCHEDULED, MANUAL }

    public enum Severity { ERROR, WARNING }

    /** One finding. ERRORs fail the run; WARNINGs (e.g. stale pending deposits) are reported only. */
    public record Finding(String check, Severity severity, Map<String, Object> details) {
    }

    /**
     * @param totalDebits  sum of all DEBIT amounts across currencies (a coarse checksum; per-currency
     *                     equality is what the GLOBAL_BALANCE check verifies)
     * @param findings     every ERROR and WARNING found
     */
    public record RunResponse(UUID id, Trigger trigger, String status, Instant startedAt, Instant finishedAt,
                              Long totalDebits, Long totalCredits, Long checkedTransactions, List<Finding> findings) {
    }
}
