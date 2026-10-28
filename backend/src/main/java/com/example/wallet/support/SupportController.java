package com.example.wallet.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanViewAnyUser;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.TransactionDtos.TransactionFilter;
import com.example.wallet.ledger.TransactionDtos.TransactionSummary;
import com.example.wallet.ledger.TransactionType;
import com.example.wallet.support.SupportDtos.SupportUserView;
import com.example.wallet.support.SupportDtos.SupportWalletView;
import com.example.wallet.support.SupportDtos.WebhookEventView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Read-only, audited investigation API for SUPPORT and ADMIN. */
@RestController
@RequestMapping("/api/v1/support")
@CanViewAnyUser
@Tag(name = "Support", description = "Read-only investigation (SUPPORT, ADMIN); every call is audited")
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    @GetMapping("/users")
    @Operation(summary = "Find users by email (contains, case-insensitive)")
    public List<SupportUserView> users(AuthUser caller, @RequestParam(required = false) String email,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return support.searchUsers(caller, email, limit);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "One user")
    public SupportUserView user(AuthUser caller, @PathVariable UUID id) {
        return support.user(caller, id);
    }

    @GetMapping("/users/{id}/wallets")
    @Operation(summary = "A user's wallets with balances")
    public List<SupportWalletView> userWallets(AuthUser caller, @PathVariable UUID id) {
        return support.userWallets(caller, id);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Search all transactions by reference, wallet, type and date range")
    public PageResponse<TransactionSummary> transactions(
            AuthUser caller,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return support.transactions(caller, new TransactionFilter(walletId, type, from, to, reference), cursor, limit);
    }

    @GetMapping("/webhook-events")
    @Operation(summary = "Inbound webhook events, newest first (status: RECEIVED, PROCESSED, REJECTED, FAILED)")
    public PageResponse<WebhookEventView> webhookEvents(
            AuthUser caller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return support.webhookEvents(caller, status, cursor, limit);
    }
}
