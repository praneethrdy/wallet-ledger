package com.example.wallet.ledger;

/**
 * Which side of a ledger entry money is on. For every account here (wallets and system
 * accounts alike): balance = SUM(CREDIT) - SUM(DEBIT). A DEBIT takes money out of an
 * account, a CREDIT puts money in.
 */
public enum Direction {
    DEBIT,
    CREDIT;

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }

    /** Effect of an entry with this direction on the account's balance. */
    public long signed(long amount) {
        return this == CREDIT ? amount : -amount;
    }
}
