package com.example.wallet.ledger;

import java.util.List;

/**
 * A reversal posts the mirror image of the original: every DEBIT becomes a CREDIT of the same
 * amount on the same account, and vice versa. Original + reversal nets to zero on every
 * account, and the original rows are never touched.
 */
public final class ReversalLegs {

    private ReversalLegs() {
    }

    public static List<Posting.Leg> mirror(List<Posting.Leg> original) {
        return original.stream().map(Posting.Leg::mirrored).toList();
    }
}
