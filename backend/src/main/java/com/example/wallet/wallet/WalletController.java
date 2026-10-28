package com.example.wallet.wallet;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanHoldWallets;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.idempotency.IdempotencyService;
import com.example.wallet.wallet.WalletDtos.BalancePoint;
import com.example.wallet.wallet.WalletDtos.CreateWalletRequest;
import com.example.wallet.wallet.WalletDtos.EntryResponse;
import com.example.wallet.wallet.WalletDtos.WalletResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/wallets")
@CanHoldWallets
@Tag(name = "Wallets", description = "The caller's own wallets")
public class WalletController {

    private final WalletService wallets;
    private final IdempotencyService idempotency;

    public WalletController(WalletService wallets, IdempotencyService idempotency) {
        this.wallets = wallets;
        this.idempotency = idempotency;
    }

    @PostMapping
    @Operation(summary = "Create a wallet (a user may own several)")
    public ResponseEntity<WalletResponse> create(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody CreateWalletRequest request,
            AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, request,
                keyId -> ResponseEntity.status(HttpStatus.CREATED).body(wallets.create(caller, request)));
    }

    @GetMapping
    @Operation(summary = "The caller's wallets with derived balances")
    public List<WalletResponse> list(AuthUser caller) {
        return wallets.list(caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One wallet with its balance (404 if it isn't yours)")
    public WalletResponse get(AuthUser caller, @PathVariable UUID id) {
        return wallets.get(caller, id);
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "Ledger entries, newest first, each with the running balance after it")
    public PageResponse<EntryResponse> entries(AuthUser caller, @PathVariable UUID id,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return wallets.entries(caller, id, cursor, limit);
    }

    @GetMapping("/{id}/balance-history")
    @Operation(summary = "Daily closing balances for sparklines (reporting time zone)")
    public List<BalancePoint> balanceHistory(AuthUser caller, @PathVariable UUID id,
                                             @RequestParam(defaultValue = "30") @Min(1) @Max(366) int days) {
        return wallets.balanceHistory(caller, id, days);
    }
}
