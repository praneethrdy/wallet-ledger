package com.example.wallet.common.error;

import java.util.Objects;

/**
 * A business error that is safe to show to the client. Its message becomes the
 * ProblemDetail {@code detail}, so never put secrets, SQL or other users' data in it.
 * Anything that is not an ApiException is treated as a bug and returned as a generic 500.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = Objects.requireNonNull(code);
    }

    public ApiException(ErrorCode code) {
        this(code, code.title());
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }
}
