package com.example.wallet.ledger;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An account in the chart of accounts: a user wallet or a system account.
 *
 * <p>Deliberately has no balance field. The balance is always derived from ledger_entries
 * (ADR-011), so there is no number here that could drift from the ledger.
 * The only mutable state is {@code status} (freeze/unfreeze) and {@code name}.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends AssignedIdEntity {

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_code")
    private SystemAccountCode systemCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    /** Optimistic-lock version, bumped by status changes. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Account(UUID id) {
        super(id);
    }

    public static Account newWallet(UUID id, UUID ownerUserId, String name, String currency, Instant now) {
        Account a = new Account(id);
        a.ownerUserId = ownerUserId;
        a.type = AccountType.USER_WALLET;
        a.currency = currency;
        a.name = name;
        a.status = AccountStatus.ACTIVE;
        a.createdAt = now;
        return a;
    }

    public boolean isUserWallet() {
        return type == AccountType.USER_WALLET;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId != null && ownerUserId.equals(userId);
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        this.status = AccountStatus.ACTIVE;
    }
}
