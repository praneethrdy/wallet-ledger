package com.example.wallet.webhook;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Public endpoint, authenticated only by the HMAC signature (no JWT, no Idempotency-Key:
 * the provider's event id is the idempotency key). The body is taken as {@code byte[]} so
 * the signature is checked against exactly the bytes that arrived.
 */
@RestController
@Tag(name = "Webhooks", description = "Inbound events from the payment provider")
public class WebhookController {

    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(path = "/api/v1/webhooks/payment-provider", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements // no bearer token
    @Operation(summary = "Payment provider event (HMAC-SHA256 signed: hex(HMAC(secret, timestamp + '.' + rawBody)))")
    public Map<String, String> receive(
            @RequestHeader(name = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawBody) {
        WebhookService.Outcome outcome = webhooks.handle(rawBody, timestamp, signature);
        return Map.of("status", outcome.name().toLowerCase());
    }
}
