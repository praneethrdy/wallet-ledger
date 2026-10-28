package com.example.wallet.ledger;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable transaction reference, e.g. {@code TXN-20261027-8F3K2QXW}: something a
 * customer can read out over the phone. Crockford base32 (no I, L, O, U) avoids look-alike
 * characters. 8 random chars = 40 bits, so a same-day collision is negligible, and the UNIQUE
 * constraint is the final guard.
 */
final class TransactionReference {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private TransactionReference() {
    }

    static String generate(Instant at) {
        StringBuilder sb = new StringBuilder("TXN-").append(DAY.format(at)).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
