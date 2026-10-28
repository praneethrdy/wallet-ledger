package com.example.wallet.deposit;

import java.util.UUID;

/** What happened when a provider event was applied to a deposit. */
public sealed interface SettlementResult {

    /** State changed (deposit credited or marked failed). */
    record Applied(UUID depositId, UUID transactionId) implements SettlementResult {
    }

    /** Already in the requested state: a repeat of an event we've applied. Nothing to do. */
    record AlreadyApplied(UUID depositId) implements SettlementResult {
    }

    /** Authentic event that can't be applied (unknown deposit, amount mismatch, conflicting state). */
    record Rejected(String reason) implements SettlementResult {
    }
}
