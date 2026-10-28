package com.example.wallet.ledger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.Money;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;

/**
 * Admin reversal: corrects a transaction by posting its mirror image. History is never
 * edited; the original keeps its rows and gains a "reversed by" link.
 *
 * <pre>
 *  original exists?                     else 404
 *  original is itself a REVERSAL?       422 REVERSAL_NOT_ALLOWED (reverse-of-reverse = re-post it)
 *  lock affected user wallets (sorted)
 *  already reversed?                    409 ALREADY_REVERSED (unique index = backstop for races)
 *  any user wallet would go negative?   422 INSUFFICIENT_FUNDS_FOR_REVERSAL
 *  post REVERSAL with mirrored legs, audit with the reason
 * </pre>
 *
 * Frozen wallets are still reversed: a reversal is a staff correction, not customer spending.
 */
@Service
public class ReversalService {

    private final JdbcTemplate jdbc;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final TransactionQueryService transactions;
    private final AuditService audit;

    public ReversalService(JdbcTemplate jdbc, AccountLocker locker, LedgerService ledger,
                           TransactionQueryService transactions, AuditService audit) {
        this.jdbc = jdbc;
        this.locker = locker;
        this.ledger = ledger;
        this.transactions = transactions;
        this.audit = audit;
    }

    @Transactional
    public TransactionDetail reverse(AuthUser admin, UUID originalId, String reason, UUID idempotencyKeyId) {
        List<Map<String, Object>> header = jdbc.queryForList(
                "SELECT type, reference FROM transactions WHERE id = ?", originalId);
        if (header.isEmpty()) {
            throw ApiException.notFound("Transaction");
        }
        TransactionType type = TransactionType.valueOf((String) header.get(0).get("type"));
        String reference = (String) header.get(0).get("reference");
        if (type == TransactionType.REVERSAL) {
            throw new ApiException(ErrorCode.REVERSAL_NOT_ALLOWED, "A reversal cannot itself be reversed");
        }

        List<OriginalLeg> legs = jdbc.query("""
                SELECT e.account_id, a.type, e.direction, e.amount, e.currency
                FROM ledger_entries e JOIN accounts a ON a.id = e.account_id
                WHERE e.transaction_id = ? ORDER BY e.id
                """, (rs, n) -> new OriginalLeg(
                new Posting.Leg(rs.getObject("account_id", UUID.class), Direction.valueOf(rs.getString("direction")),
                        Money.of(rs.getLong("amount"), rs.getString("currency").trim())),
                AccountType.valueOf(rs.getString("type"))), originalId);

        List<UUID> userWallets = legs.stream()
                .filter(l -> l.accountType() == AccountType.USER_WALLET)
                .map(l -> l.leg().accountId()).distinct().toList();
        locker.lockInOrder(userWallets);

        Integer already = jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE reversal_of_transaction_id = ?", Integer.class, originalId);
        if (already != null && already > 0) {
            throw new ApiException(ErrorCode.ALREADY_REVERSED, "Transaction " + reference + " is already reversed");
        }

        List<Posting.Leg> mirrored = ReversalLegs.mirror(legs.stream().map(OriginalLeg::leg).toList());
        Map<UUID, Long> effect = new HashMap<>();
        mirrored.forEach(l -> effect.merge(l.accountId(), l.signedAmount(), Math::addExact));
        Map<UUID, Long> balances = ledger.balancesOf(userWallets);
        for (UUID wallet : userWallets) {
            if (balances.get(wallet) + effect.getOrDefault(wallet, 0L) < 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS_FOR_REVERSAL,
                        "Reversing " + reference + " would make a wallet negative");
            }
        }

        PostedTransaction reversal;
        try {
            reversal = ledger.post(new Posting(
                    TransactionType.REVERSAL,
                    "Reversal of " + reference + ": " + reason,
                    admin.id(),
                    idempotencyKeyId,
                    originalId,
                    Map.of("reason", reason, "originalReference", reference),
                    mirrored));
        } catch (DuplicateKeyException e) {
            // Two admins raced past the check above; the partial unique index decided.
            throw new ApiException(ErrorCode.ALREADY_REVERSED, "Transaction " + reference + " is already reversed");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("originalTransactionId", originalId);
        details.put("originalReference", reference);
        details.put("reversalTransactionId", reversal.id());
        details.put("reason", reason);
        audit.record(admin.id(), "TRANSACTION_REVERSED", "transaction", originalId, details, null);

        return transactions.detailForStaff(reversal.id());
    }

    private record OriginalLeg(Posting.Leg leg, AccountType accountType) {
    }
}
