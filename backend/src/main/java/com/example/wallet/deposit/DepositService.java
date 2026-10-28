package com.example.wallet.deposit;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.audit.AuditService;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.common.CurrencyMismatchException;
import com.example.wallet.common.Money;
import com.example.wallet.common.UuidV7;
import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.deposit.DepositDtos.CreateDepositRequest;
import com.example.wallet.deposit.DepositDtos.DepositResponse;
import com.example.wallet.ledger.Account;
import com.example.wallet.ledger.AccountLocker;
import com.example.wallet.ledger.AccountRepository;
import com.example.wallet.ledger.AccountType;
import com.example.wallet.ledger.LedgerService;
import com.example.wallet.ledger.PostedTransaction;
import com.example.wallet.ledger.Posting;
import com.example.wallet.ledger.SystemAccountCode;
import com.example.wallet.ledger.SystemAccounts;
import com.example.wallet.ledger.TransactionType;

/**
 * Deposit lifecycle: PENDING (customer asks) -> SUCCEEDED (posted to the ledger) or FAILED,
 * driven only by authenticated provider webhooks.
 */
@Service
public class DepositService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DepositRepository deposits;
    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final SystemAccounts systemAccounts;
    private final AuditService audit;
    private final Clock clock;

    public DepositService(DepositRepository deposits, AccountRepository accounts, AccountLocker locker,
                          LedgerService ledger, SystemAccounts systemAccounts, AuditService audit, Clock clock) {
        this.deposits = deposits;
        this.accounts = accounts;
        this.locker = locker;
        this.ledger = ledger;
        this.systemAccounts = systemAccounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DepositResponse create(AuthUser caller, CreateDepositRequest request) {
        Money amount = Money.fromRequest(request.amount(), request.currency());
        Account wallet = accounts.findByIdAndOwnerUserIdAndType(request.walletId(), caller.id(), AccountType.USER_WALLET)
                .orElseThrow(() -> ApiException.notFound("Wallet"));
        if (!wallet.isActive()) {
            throw new ApiException(ErrorCode.WALLET_FROZEN, "Wallet is not active");
        }
        if (!wallet.getCurrency().equals(amount.currency())) {
            throw new CurrencyMismatchException(wallet.getCurrency(), amount.currency());
        }
        // In production this is where we'd call the provider's "create payment" API and store
        // the reference it returns. The mock provider accepts any reference we hand it.
        String providerReference = "mock_" + HexFormat.of().formatHex(randomBytes(12));
        Deposit deposit = deposits.save(new Deposit(UuidV7.generate(clock), wallet.getId(), amount.amountMinor(),
                amount.currency(), providerReference, clock.instant()));
        audit.record(caller.id(), "DEPOSIT_CREATED", "deposit", deposit.getId(),
                Map.of("amount", amount.amountMinor(), "currency", amount.currency()), null);
        return DepositResponse.from(deposit);
    }

    @Transactional(readOnly = true)
    public DepositResponse get(AuthUser caller, UUID depositId) {
        return deposits.findOwned(depositId, caller.id())
                .map(DepositResponse::from)
                .orElseThrow(() -> ApiException.notFound("Deposit"));
    }

    /**
     * Provider says the money arrived. In the webhook's transaction: lock the deposit row
     * (two deliveries can't both credit), verify it matches, post DEBIT EXTERNAL_FUNDING /
     * CREDIT wallet, link the transaction.
     *
     * <p>A frozen wallet IS credited: the customer's money has already left their bank, and the
     * ledger must reflect reality. Freezing blocks spending, not receiving settled funds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementResult settleSucceeded(String providerReference, long amount, String currency) {
        Deposit deposit = deposits.findByProviderReferenceForUpdate(providerReference).orElse(null);
        if (deposit == null) {
            return new SettlementResult.Rejected("Unknown deposit reference");
        }
        switch (deposit.getStatus()) {
            case SUCCEEDED -> {
                return new SettlementResult.AlreadyApplied(deposit.getId());
            }
            case FAILED -> {
                return new SettlementResult.Rejected("Deposit already marked FAILED");
            }
            case PENDING -> {
                // continue below
            }
        }
        if (deposit.getAmount() != amount || !deposit.getCurrency().equals(currency)) {
            // Never credit a different amount than the customer asked for.
            return new SettlementResult.Rejected("Amount or currency does not match the deposit");
        }

        Map<UUID, Account> locked = locker.lockInOrder(List.of(deposit.getWalletId()));
        Account wallet = locked.get(deposit.getWalletId());
        Money money = Money.of(amount, currency);
        PostedTransaction posted = ledger.post(new Posting(
                TransactionType.DEPOSIT,
                "Deposit " + providerReference,
                wallet.getOwnerUserId(),
                null,
                null,
                Map.of("depositId", deposit.getId().toString(), "providerReference", providerReference),
                List.of(Posting.Leg.debit(systemAccounts.id(SystemAccountCode.EXTERNAL_FUNDING, currency), money),
                        Posting.Leg.credit(wallet.getId(), money))));
        deposit.succeeded(posted.id(), clock.instant());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionId", posted.id());
        details.put("amount", amount);
        details.put("currency", currency);
        audit.record(null, "DEPOSIT_SUCCEEDED", "deposit", deposit.getId(), details, null);
        return new SettlementResult.Applied(deposit.getId(), posted.id());
    }

    /** Provider says the payment failed: no ledger entries, just the status. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementResult markFailed(String providerReference) {
        Deposit deposit = deposits.findByProviderReferenceForUpdate(providerReference).orElse(null);
        if (deposit == null) {
            return new SettlementResult.Rejected("Unknown deposit reference");
        }
        return switch (deposit.getStatus()) {
            case FAILED -> new SettlementResult.AlreadyApplied(deposit.getId());
            case SUCCEEDED -> new SettlementResult.Rejected("Deposit already SUCCEEDED; reverse it instead");
            case PENDING -> {
                deposit.failed(clock.instant());
                audit.record(null, "DEPOSIT_FAILED", "deposit", deposit.getId(), null, null);
                yield new SettlementResult.Applied(deposit.getId(), null);
            }
        };
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
