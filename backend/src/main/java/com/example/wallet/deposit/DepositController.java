package com.example.wallet.deposit;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanMoveMoney;
import com.example.wallet.deposit.DepositDtos.CreateDepositRequest;
import com.example.wallet.deposit.DepositDtos.DepositResponse;
import com.example.wallet.idempotency.IdempotencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/deposits")
@CanMoveMoney
@Tag(name = "Deposits", description = "Add money via the (mock) payment provider")
public class DepositController {

    private final DepositService deposits;
    private final IdempotencyService idempotency;

    public DepositController(DepositService deposits, IdempotencyService idempotency) {
        this.deposits = deposits;
        this.idempotency = idempotency;
    }

    @PostMapping
    @Operation(summary = "Start a deposit; it stays PENDING until the provider's webhook arrives")
    public ResponseEntity<DepositResponse> create(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody CreateDepositRequest request,
            AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, request,
                keyId -> ResponseEntity.status(HttpStatus.CREATED).body(deposits.create(caller, request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Deposit status (poll until SUCCEEDED or FAILED)")
    public DepositResponse get(AuthUser caller, @PathVariable UUID id) {
        return deposits.get(caller, id);
    }
}
