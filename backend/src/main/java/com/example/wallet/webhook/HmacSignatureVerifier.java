package com.example.wallet.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies {@code X-Webhook-Signature = hex(HMAC-SHA256(secret, timestamp + "." + rawBody))}.
 *
 * <ul>
 *   <li><b>Raw bytes:</b> the MAC covers the exact bytes received. Parsing JSON first and
 *       re-serializing would change whitespace and key order, and the MAC would never match;
 *       worse, it tempts you to verify something other than what you'll act on.</li>
 *   <li><b>Constant-time compare</b> ({@link MessageDigest#isEqual}): {@code String.equals}
 *       returns at the first differing byte, so response timing leaks how many leading bytes
 *       of a forged signature were right.</li>
 *   <li><b>Timestamp inside the MAC + max skew:</b> a captured request can't be replayed
 *       later, because the timestamp can't be changed without breaking the signature.</li>
 *   <li><b>Two secrets:</b> during rotation the provider may sign with the new or the old one.</li>
 * </ul>
 */
public class HmacSignatureVerifier {

    public enum Result { VALID, INVALID_SIGNATURE, STALE_TIMESTAMP, MALFORMED }

    private static final String ALGORITHM = "HmacSHA256";

    private final List<byte[]> secrets;
    private final Duration tolerance;
    private final Clock clock;

    public HmacSignatureVerifier(List<String> secrets, Duration tolerance, Clock clock) {
        this.secrets = secrets.stream().filter(Objects::nonNull).filter(s -> !s.isBlank())
                .map(s -> s.getBytes(StandardCharsets.UTF_8)).toList();
        if (this.secrets.isEmpty()) {
            throw new IllegalStateException("At least one webhook secret is required");
        }
        this.tolerance = tolerance;
        this.clock = clock;
    }

    public Result verify(String timestampHeader, String signatureHeader, byte[] rawBody) {
        if (timestampHeader == null || signatureHeader == null || rawBody == null) {
            return Result.MALFORMED;
        }
        long timestamp;
        byte[] provided;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
            provided = HexFormat.of().parseHex(signatureHeader.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return Result.MALFORMED;
        }

        boolean matches = false;
        for (byte[] secret : secrets) {
            // No early exit: always compute every candidate, so timing doesn't reveal which matched.
            matches |= MessageDigest.isEqual(mac(secret, timestamp, rawBody), provided);
        }
        if (!matches) {
            return Result.INVALID_SIGNATURE;
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - timestamp);
        return skew > tolerance.toSeconds() ? Result.STALE_TIMESTAMP : Result.VALID;
    }

    /** What the provider (and our mock provider / scripts) computes. */
    public static String sign(String secret, long timestamp, byte[] rawBody) {
        return HexFormat.of().formatHex(mac(secret.getBytes(StandardCharsets.UTF_8), timestamp, rawBody));
    }

    private static byte[] mac(byte[] secret, long timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
