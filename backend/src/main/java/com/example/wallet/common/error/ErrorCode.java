package com.example.wallet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. Clients branch on {@code code}, never on the
 * human-readable {@code detail}, so detail text can change without breaking anyone.
 *
 * <p>Status choice (ADR-006): 400 = the request itself is malformed; 422 = well-formed but a
 * business rule refuses it; 409 = conflicts with the current state of a resource.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "Request rejected"),

    WALLET_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "Wallet is frozen"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    INSUFFICIENT_FUNDS_FOR_REVERSAL(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds for reversal"),
    SAME_WALLET_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot transfer to the same wallet"),

    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key reused with a different request"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this Idempotency-Key is in progress"),
    ALREADY_REVERSED(HttpStatus.CONFLICT, "Transaction already reversed"),

    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Invalid signature"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Code for errors raised by Spring itself (no ApiException), derived from the HTTP status. */
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            default -> status >= 500 ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
