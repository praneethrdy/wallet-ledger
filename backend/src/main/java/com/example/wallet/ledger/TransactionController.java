package com.example.wallet.ledger;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanHoldWallets;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;
import com.example.wallet.ledger.TransactionDtos.TransactionFilter;
import com.example.wallet.ledger.TransactionDtos.TransactionSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/transactions")
@CanHoldWallets
@Tag(name = "Transactions", description = "Transactions touching the caller's own wallets")
public class TransactionController {

    private final TransactionQueryService queries;

    public TransactionController(TransactionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "List the caller's transactions, newest first (keyset pagination)")
    public PageResponse<TransactionSummary> list(
            AuthUser caller,
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return queries.listForCaller(caller, new TransactionFilter(walletId, type, from, to, reference), cursor, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "A transaction with all legs and reversal links (404 unless it touches your wallet)")
    public TransactionDetail get(AuthUser caller, @PathVariable UUID id) {
        return queries.detailForCaller(caller, id);
    }
}
