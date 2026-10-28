package com.example.wallet.admin;

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

import com.example.wallet.admin.AdminDtos.AdminWalletView;
import com.example.wallet.admin.AdminDtos.Overview;
import com.example.wallet.admin.AdminDtos.ReverseRequest;
import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.AdminOnly;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.idempotency.IdempotencyService;
import com.example.wallet.ledger.AccountStatus;
import com.example.wallet.ledger.ReversalService;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/admin")
@AdminOnly
@Tag(name = "Admin", description = "Reversals, wallet freeze and operations overview (ADMIN only)")
public class AdminController {

    private final ReversalService reversals;
    private final AdminService admin;
    private final IdempotencyService idempotency;

    public AdminController(ReversalService reversals, AdminService admin, IdempotencyService idempotency) {
        this.reversals = reversals;
        this.admin = admin;
        this.idempotency = idempotency;
    }

    @PostMapping("/transactions/{id}/reverse")
    @Operation(summary = "Reverse a transaction by posting mirror legs (never edits the original)")
    public ResponseEntity<TransactionDetail> reverse(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody ReverseRequest request,
            AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, request, keyId -> ResponseEntity
                .status(HttpStatus.CREATED).body(reversals.reverse(caller, id, request.reason().trim(), keyId)));
    }

    @GetMapping("/wallets")
    @Operation(summary = "Search wallets by owner email (contains, case-insensitive) and/or status")
    public PageResponse<AdminWalletView> searchWallets(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return admin.searchWallets(email, status, cursor, limit);
    }

    @PostMapping("/wallets/{id}/freeze")
    @Operation(summary = "Freeze a wallet: blocks transfers in and out (no-op if already frozen)")
    public ResponseEntity<AdminWalletView> freeze(@RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
                                                  @PathVariable UUID id, AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, null,
                keyId -> ResponseEntity.ok(admin.setFrozen(caller, id, true)));
    }

    @PostMapping("/wallets/{id}/unfreeze")
    @Operation(summary = "Unfreeze a wallet (no-op if already active)")
    public ResponseEntity<AdminWalletView> unfreeze(@RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
                                                    @PathVariable UUID id, AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, null,
                keyId -> ResponseEntity.ok(admin.setFrozen(caller, id, false)));
    }

    @GetMapping("/overview")
    @Operation(summary = "Today's volume and transfer count, failed webhooks (24h), last reconciliation")
    public Overview overview() {
        return admin.overview();
    }
}
