package com.example.wallet.wallet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.example.wallet.ledger.AccountStatus;
import com.example.wallet.ledger.Direction;
import com.example.wallet.ledger.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public final class WalletDtos {

    private WalletDtos() {
    }

    /** {@code currency} is optional and defaults to the platform default (INR). */
    public record CreateWalletRequest(
            @NotBlank @Size(max = 60) String name,
            @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code") String currency) {
    }

    /** {@code balance} is derived from the ledger at read time, in minor units. */
    public record WalletResponse(UUID id, String name, String currency, AccountStatus status, long balance,
                                 Instant createdAt) {
    }

    /** One ledger entry on a wallet, with the wallet's balance right after it. */
    public record EntryResponse(
            UUID transactionId,
            String reference,
            TransactionType type,
            @Schema(nullable = true) String description,
            Direction direction,
            long amount,
            String currency,
            long runningBalance,
            Instant createdAt) {
    }

    /** Closing balance at the end of {@code date} (in the reporting time zone). */
    public record BalancePoint(LocalDate date, long balance) {
    }
}
