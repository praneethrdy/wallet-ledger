package com.example.wallet.demo;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanMoveMoney;
import com.example.wallet.deposit.DepositDtos.DepositResponse;
import com.example.wallet.idempotency.IdempotencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@CanMoveMoney
@Tag(name = "Deposits", description = "Add money via the payment provider")
public class DemoPaymentController {

    public record DemoPaymentRequest(@NotNull DemoPaymentService.Outcome outcome) {
    }

    private final DemoPaymentService demo;
    private final IdempotencyService idempotency;

    public DemoPaymentController(DemoPaymentService demo, IdempotencyService idempotency) {
        this.demo = demo;
        this.idempotency = idempotency;
    }

    @PostMapping("/api/v1/deposits/{id}/demo-payment")
    @Operation(summary = "Demo only: complete your own pending deposit with a simulated payment "
            + "(404 unless payments.demo.enabled). Settles through the normal signed-webhook path.")
    public ResponseEntity<DepositResponse> complete(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody DemoPaymentRequest request,
            AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, request,
                keyId -> ResponseEntity.ok(demo.complete(caller, id, request.outcome())));
    }
}
