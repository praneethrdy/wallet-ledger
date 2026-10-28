package com.example.wallet.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models for transactions. Amounts are minor units (paise for INR). */
public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** A transaction with all its legs, plus reversal links in both directions. */
    public record TransactionDetail(
            UUID id,
            String reference,
            TransactionType type,
            String description,
            Instant createdAt,
            UUID reversalOfTransactionId,
            UUID reversedByTransactionId,
            List<LegView> legs) {
    }

    /**
     * One leg. For a customer, another user's wallet shows as "Another user's wallet": the
     * id is visible (needed to pay them back), the private wallet name is not.
     */
    public record LegView(UUID accountId, String accountName, AccountType accountType,
                          Direction direction, long amount, String currency) {
    }

    /**
     * One row of a transaction list.
     *
     * @param amount    size of the transaction (sum of its debits) in {@code currency}
     * @param netAmount effect on the viewer's wallets (or on the filtered wallet): positive =
     *                  money in, negative = money out, 0 = between the viewer's own wallets.
     *                  Null in staff views without a wallet filter.
     */
    public record TransactionSummary(
            UUID id,
            String reference,
            TransactionType type,
            String description,
            Instant createdAt,
            String currency,
            long amount,
            Long netAmount,
            UUID reversalOfTransactionId,
            UUID reversedByTransactionId) {
    }

    /** Optional filters for transaction lists; {@code from} inclusive, {@code to} exclusive. */
    public record TransactionFilter(UUID walletId, TransactionType type, Instant from, Instant to, String reference) {
    }
}
