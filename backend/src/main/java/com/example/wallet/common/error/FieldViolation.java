package com.example.wallet.common.error;

/** One entry of the ProblemDetail {@code errors[]} array for validation failures. */
public record FieldViolation(String field, String message) {
}
