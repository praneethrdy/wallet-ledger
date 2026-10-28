package com.example.wallet.transfer;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Move {@code amount} minor units (paise) from one of the caller's wallets to any wallet.
 * The caller is taken from the token; there is deliberately no "userId" field.
 */
public record TransferRequest(
        @NotNull UUID fromWalletId,
        @NotNull UUID toWalletId,
        @Positive long amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency,
        @Size(max = 140) String note) {
}
