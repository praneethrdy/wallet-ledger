package com.example.wallet.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** SELECT ... FOR UPDATE. Only call through {@link AccountLocker}, which fixes the order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    /** Ownership-scoped lookup: "not found" and "not yours" are the same result (ADR-009). */
    Optional<Account> findByIdAndOwnerUserIdAndType(UUID id, UUID ownerUserId, AccountType type);

    boolean existsByIdAndOwnerUserIdAndType(UUID id, UUID ownerUserId, AccountType type);

    List<Account> findByOwnerUserIdAndTypeOrderByCreatedAtAsc(UUID ownerUserId, AccountType type);

    List<Account> findByType(AccountType type);
}
