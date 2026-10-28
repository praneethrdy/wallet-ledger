package com.example.wallet.auth;

import java.time.Instant;
import java.util.UUID;

import com.example.wallet.common.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One refresh token in a rotation chain. Only SHA-256(token) is stored: a database leak
 * does not hand out usable tokens. All tokens descended from one login share a familyId.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends AssignedIdEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "user_agent")
    private String userAgent;

    @Column
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    RefreshToken(UUID id, UUID userId, byte[] tokenHash, UUID familyId, Instant now, Instant expiresAt,
                 ClientInfo client) {
        super(id);
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.userAgent = client.userAgent();
        this.ip = client.ip();
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    void rotatedTo(UUID successorId, Instant now) {
        this.revokedAt = now;
        this.replacedBy = successorId;
    }
}
