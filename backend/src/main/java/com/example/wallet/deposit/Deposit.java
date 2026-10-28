package com.example.wallet.deposit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.wallet.common.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A request to add money via the payment provider. PENDING until the signed webhook arrives. */
@Entity
@Table(name = "deposits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deposit extends AssignedIdEntity {

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private long amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    Deposit(UUID id, UUID walletId, long amount, String currency, String providerReference, Instant now) {
        super(id);
        this.walletId = walletId;
        this.amount = amount;
        this.currency = currency;
        this.providerReference = providerReference;
        this.status = DepositStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void succeeded(UUID transactionId, Instant now) {
        this.status = DepositStatus.SUCCEEDED;
        this.transactionId = transactionId;
        this.updatedAt = now;
    }

    void failed(Instant now) {
        this.status = DepositStatus.FAILED;
        this.updatedAt = now;
    }
}
