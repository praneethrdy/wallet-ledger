package com.example.wallet.transfer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.CurrencyMismatchException;
import com.example.wallet.common.Money;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountLocker;
import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.ledger.AccountType;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.PostedTransaction;
import com.example.wallet.ledger.Posting;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;
import com.example.wallet.ledger.TransactionQueryService;
import com.example.wallet.ledger.TransactionType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Wallet-to-wallet transfer: one DB transaction, two locked rows, one balanced posting.
 *
 * <pre>
 *  1. cheap ownership check (404 before taking any lock on someone else's row)
 *  2. lock both wallets, in global UUID order       -> no deadlocks
 *  3. re-check status/currency under the lock       -> no freeze racing past us
 *  4. derive sender balance UNDER the lock          -> no double spend
 *  5. post DEBIT sender / CREDIT receiver, audit, commit (releases the locks)
 * </pre>
 *
 * <p>Why step 4 is safe at READ COMMITTED: every writer to a wallet's entries (transfer,
 * deposit settlement, reversal) first locks that wallet's row. Once we hold the lock, no other
 * write to it can be in flight, and the SUM statement takes a fresh snapshot that includes
 * everything committed before we got the lock. Under REPEATABLE READ the snapshot would be
 * from the start of the transaction (before the wait), and we'd miss a just-committed debit.
 */
@Service
public class TransferService {

    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final TransactionQueryService transactions;
    private final AuditService audit;
    private final MeterRegistry registry;

    public TransferService(AccountRepository accounts, AccountLocker locker, LedgerService ledger,
                           TransactionQueryService transactions, AuditService audit, MeterRegistry registry) {
        this.accounts = accounts;
        this.locker = locker;
        this.ledger = ledger;
        this.transactions = transactions;
        this.audit = audit;
        this.registry = registry;
    }

    @Transactional
    public TransactionDetail transfer(AuthUser caller, TransferRequest request, UUID idempotencyKeyId) {
        try {
            TransactionDetail result = doTransfer(caller, request, idempotencyKeyId);
            count("success");
            return result;
        } catch (ApiException e) {
            count(e.code().name().toLowerCase(Locale.ROOT));
            throw e;
        }
    }

    private TransactionDetail doTransfer(AuthUser caller, TransferRequest request, UUID idempotencyKeyId) {
        UUID fromId = request.fromWalletId();
        UUID toId = request.toWalletId();
        if (fromId.equals(toId)) {
            throw new ApiException(ErrorCode.SAME_WALLET_TRANSFER, "Source and destination wallets must differ");
        }
        Money amount = Money.fromRequest(request.amount(), request.currency());

        if (!accounts.existsByIdAndOwnerUserIdAndType(fromId, caller.id(), AccountType.USER_WALLET)) {
            throw ApiException.notFound("Wallet");
        }

        Map<UUID, Account> locked = locker.lockInOrder(List.of(fromId, toId));
        Account source = locked.get(fromId);
        Account destination = locked.get(toId);
        if (source == null) {
            throw ApiException.notFound("Wallet");
        }
        if (destination == null || !destination.isUserWallet()) {
            throw ApiException.notFound("Destination wallet");
        }
        if (!source.isActive()) {
            throw new ApiException(ErrorCode.WALLET_FROZEN, "Source wallet is not active");
        }
        if (!destination.isActive()) {
            throw new ApiException(ErrorCode.WALLET_FROZEN, "Destination wallet is not active");
        }
        if (!source.getCurrency().equals(amount.currency())) {
            throw new CurrencyMismatchException(source.getCurrency(), amount.currency());
        }
        if (!destination.getCurrency().equals(amount.currency())) {
            throw new CurrencyMismatchException(destination.getCurrency(), amount.currency());
        }

        long balance = ledger.balanceOf(fromId);
        if (balance < amount.amountMinor()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in source wallet");
        }

        PostedTransaction posted = ledger.post(new Posting(
                TransactionType.TRANSFER,
                request.note(),
                caller.id(),
                idempotencyKeyId,
                null,
                Map.of(),
                List.of(Posting.Leg.debit(fromId, amount), Posting.Leg.credit(toId, amount))));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromWalletId", fromId);
        details.put("toWalletId", toId);
        details.put("amount", amount.amountMinor());
        details.put("currency", amount.currency());
        audit.record(caller.id(), "TRANSFER_COMPLETED", "transaction", posted.id(), details, null);

        return transactions.detailForCaller(caller, posted.id());
    }

    private void count(String outcome) {
        Counter.builder("wallet.transfers").tag("outcome", outcome)
                .description("Transfer attempts by outcome").register(registry).increment();
    }
}
