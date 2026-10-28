package com.example.wallet.common;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;

public class CurrencyMismatchException extends ApiException {

    public CurrencyMismatchException(String expected, String actual) {
        super(ErrorCode.CURRENCY_MISMATCH, "Currency mismatch: expected " + expected + " but got " + actual);
    }
}
