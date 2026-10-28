package com.example.wallet.support;

import java.time.Instant;
import java.util.UUID;

import com.example.wallet.ledger.AccountStatus;
import com.example.wallet.user.Role;
import com.example.wallet.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SupportDtos {

    private SupportDtos() {
    }

    public record SupportUserView(UUID id, String email, Role role, UserStatus status, Instant createdAt) {
    }

    public record SupportWalletView(UUID id, String name, String currency, AccountStatus status, long balance,
                                    Instant createdAt) {
    }

    public record WebhookEventView(UUID id, String provider, String providerEventId, String eventType, String status,
                                   @Schema(nullable = true) String error,
                                   @Schema(nullable = true) JsonNode payload,
                                   Instant receivedAt,
                                   @Schema(nullable = true) Instant processedAt) {
    }
}
