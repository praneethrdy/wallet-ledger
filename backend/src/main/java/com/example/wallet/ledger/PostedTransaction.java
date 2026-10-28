package com.example.wallet.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of {@link LedgerService#post}: the stored header plus the legs as posted. */
public record PostedTransaction(UUID id, String reference, TransactionType type, Instant createdAt,
                                List<Posting.Leg> legs) {
}
