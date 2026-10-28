package com.example.wallet.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.wallet.ledger.AccountStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record ReverseRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** A wallet as staff see it: includes the owner and the real name. */
    public record AdminWalletView(UUID id, String name, String currency, AccountStatus status, long balance,
                                  UUID ownerUserId, String ownerEmail, Instant createdAt) {
    }

    public record CurrencyAmount(String currency, long amount) {
    }

    public record LastReconciliation(UUID id, String status, Instant startedAt,
                                     @Schema(nullable = true) Instant finishedAt) {
    }

    /**
     * @param volumeToday       sum of transfer amounts since local midnight, per currency
     * @param failedWebhooks24h FAILED + REJECTED webhook events in the last 24h
     */
    public record Overview(List<CurrencyAmount> volumeToday, long transfersToday, long failedWebhooks24h,
                           @Schema(nullable = true) LastReconciliation lastReconciliation) {
    }
}
