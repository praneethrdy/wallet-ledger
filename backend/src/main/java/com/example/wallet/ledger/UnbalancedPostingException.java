package com.example.wallet.ledger;

/**
 * A posting broke a double-entry rule. Always a programming error (services build legs, not
 * users), so it is deliberately NOT an ApiException: it surfaces as a 500 and gets logged.
 */
public class UnbalancedPostingException extends IllegalArgumentException {

    public UnbalancedPostingException(String message) {
        super(message);
    }
}
