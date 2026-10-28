package com.example.wallet.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.auth.AuthUser;
import com.example.wallet.auth.access.CanMoveMoney;
import com.example.wallet.idempotency.IdempotencyService;
import com.example.wallet.ledger.TransactionDtos.TransactionDetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private final TransferService transfers;
    private final IdempotencyService idempotency;

    public TransferController(TransferService transfers, IdempotencyService idempotency) {
        this.transfers = transfers;
        this.idempotency = idempotency;
    }

    @PostMapping
    @CanMoveMoney
    @Operation(summary = "Transfer between wallets (customers only, from their own wallet)")
    public ResponseEntity<TransactionDetail> transfer(
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            AuthUser caller, HttpServletRequest http) {
        return idempotency.execute(caller, idempotencyKey, http, request,
                keyId -> ResponseEntity.status(HttpStatus.CREATED).body(transfers.transfer(caller, request, keyId)));
    }
}
