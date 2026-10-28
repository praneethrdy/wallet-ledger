package com.example.wallet.idempotency;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;

/**
 * Our IN_PROGRESS reservation was taken over by another request (we were slower than
 * {@code staleAfter}). The business transaction is rolled back, so the other request's
 * outcome is the only one that counts. Never stored or released: the row isn't ours.
 */
class ReservationLostException extends ApiException {

    ReservationLostException() {
        super(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, "This request was superseded by a retry with the same Idempotency-Key");
    }
}
