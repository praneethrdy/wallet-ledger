package com.example.wallet.deposit;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class DepositDtos {

    private DepositDtos() {
    }

    public record CreateDepositRequest(
            @NotNull UUID walletId,
            @Positive long amount,
            @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency) {
    }

    /**
     * {@code providerReference} is what the payment provider will quote in its webhook.
     * {@code transactionId} is set once the deposit has SUCCEEDED and been posted.
     */
    public record DepositResponse(UUID id, UUID walletId, long amount, String currency, String providerReference,
                                  DepositStatus status, UUID transactionId, Instant createdAt, Instant updatedAt) {

        static DepositResponse from(Deposit d) {
            return new DepositResponse(d.getId(), d.getWalletId(), d.getAmount(), d.getCurrency(),
                    d.getProviderReference(), d.getStatus(), d.getTransactionId(), d.getCreatedAt(), d.getUpdatedAt());
        }
    }
}
