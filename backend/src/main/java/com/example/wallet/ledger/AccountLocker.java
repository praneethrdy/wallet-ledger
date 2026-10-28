package com.example.wallet.ledger;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The single place where account rows are locked.
 *
 * <p>WHY a fixed order: transfer A->B locks A then B; a concurrent B->A would lock B then A.
 * Each now holds the lock the other wants: a deadlock. Postgres detects it after
 * deadlock_timeout (1s) and kills one transaction. If every code path locks in the same global
 * order (here: UUID text order, which is also Postgres' uuid ordering), the cycle can't form.
 *
 * <p>Rules: lock only USER wallets that a posting touches. System accounts are never locked:
 * they may go negative, so nothing is checked against their balance, and locking them would
 * make every deposit in the system wait in one queue.
 */
@Component
public class AccountLocker {

    public static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final AccountRepository accounts;
    private final Timer lockWait;

    public AccountLocker(AccountRepository accounts, MeterRegistry registry) {
        this.accounts = accounts;
        this.lockWait = Timer.builder("wallet.ledger.lock.wait")
                .description("Time spent acquiring account row locks")
                .register(registry);
    }

    /**
     * Locks the given accounts (SELECT ... FOR UPDATE) in deterministic order and returns the
     * ones that exist. Must run inside the caller's transaction: the locks are held until it
     * commits or rolls back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Account> lockInOrder(Collection<UUID> accountIds) {
        List<UUID> ordered = accountIds.stream().distinct().sorted(LOCK_ORDER).toList();
        Map<UUID, Account> locked = new LinkedHashMap<>();
        lockWait.record(() -> {
            for (UUID id : ordered) {
                accounts.findByIdForUpdate(id).ifPresent(a -> locked.put(id, a));
            }
        });
        return locked;
    }
}
