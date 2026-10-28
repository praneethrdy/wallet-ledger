package com.example.wallet.reconciliation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.AdminOnly;
import com.example.wallet.auth.access.CanViewAnyUser;
import com.example.wallet.common.web.PageResponse;
import com.example.wallet.idempotency.IdempotencyService;
import com.example.wallet.reconciliation.ReconciliationDtos.RunResponse;
import com.example.wallet.reconciliation.ReconciliationDtos.Trigger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** RBAC: SUPPORT may view runs, only ADMIN may start one. */
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@Tag(name = "Reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliation;
    private final IdempotencyService idempotency;

    public ReconciliationController(ReconciliationService reconciliation, IdempotencyService idempotency) {
        this.reconciliation = reconciliation;
        this.idempotency = idempotency;
    }

    @PostMapping("/run")
    @AdminOnly
    @Operation(summary = "Run reconciliation now (synchronously) and return the result")
    public ResponseEntity<RunResponse> run(@RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
                                           AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, null,
                keyId -> ResponseEntity.status(HttpStatus.CREATED).body(reconciliation.run(Trigger.MANUAL)));
    }

    @GetMapping("/runs")
    @CanViewAnyUser
    @Operation(summary = "Past runs, newest first")
    public PageResponse<RunResponse> runs(@RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return reconciliation.list(cursor, limit);
    }

    @GetMapping("/runs/{id}")
    @CanViewAnyUser
    @Operation(summary = "One run with all findings")
    public RunResponse run(@PathVariable UUID id) {
        return reconciliation.get(id);
    }
}
