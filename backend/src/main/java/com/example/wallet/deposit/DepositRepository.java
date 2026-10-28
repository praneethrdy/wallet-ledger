package com.example.wallet.deposit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

    /** Locks the deposit while settling it, so two webhooks for it can't both credit the wallet. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deposit d where d.providerReference = :ref")
    Optional<Deposit> findByProviderReferenceForUpdate(@Param("ref") String providerReference);

    Optional<Deposit> findByProviderReference(String providerReference);

    /** Ownership-scoped: another user's deposit is "not found". */
    @Query("""
            select d from Deposit d
            where d.id = :id and d.walletId in (select a.id from Account a where a.ownerUserId = :owner)
            """)
    Optional<Deposit> findOwned(@Param("id") UUID id, @Param("owner") UUID ownerUserId);
}
