package com.example.wallet.ledger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.example.wallet.common.Money;

/**
 * Everything needed to post one balanced transaction to the ledger.
 *
 * @param idempotencyKeyId soft link to the idempotency key that produced it (ADR-004), or null
 * @param reversalOf       the transaction this one reverses (REVERSAL only), or null
 */
public record Posting(
        TransactionType type,
        String description,
        UUID initiatedBy,
        UUID idempotencyKeyId,
        UUID reversalOf,
        Map<String, Object> metadata,
        List<Leg> legs) {

    public Posting {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initiatedBy, "initiatedBy");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** One side of the posting: money in (CREDIT) or out (DEBIT) of an account. */
    public record Leg(UUID accountId, Direction direction, Money amount) {

        public Leg {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
        }

        public static Leg debit(UUID accountId, Money amount) {
            return new Leg(accountId, Direction.DEBIT, amount);
        }

        public static Leg credit(UUID accountId, Money amount) {
            return new Leg(accountId, Direction.CREDIT, amount);
        }

        /** The opposite entry: what a reversal posts to undo this one. */
        public Leg mirrored() {
            return new Leg(accountId, direction.opposite(), amount);
        }

        /** Effect on the account's balance (credits positive). */
        public long signedAmount() {
            return direction.signed(amount.amountMinor());
        }
    }
}
