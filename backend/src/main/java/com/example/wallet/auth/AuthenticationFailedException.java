package com.example.wallet.auth;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;

/**
 * Login or refresh failed. AuthService's transactions do NOT roll back on this exception,
 * so the audit row (and, on token reuse, the family revocation) is committed even though
 * the client gets a 401.
 */
class AuthenticationFailedException extends ApiException {

    AuthenticationFailedException(String detail) {
        super(ErrorCode.UNAUTHORIZED, detail);
    }
}
