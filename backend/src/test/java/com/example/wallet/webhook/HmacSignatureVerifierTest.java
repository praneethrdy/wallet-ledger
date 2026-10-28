package com.example.wallet.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.wallet.webhook.HmacSignatureVerifier.Result;

class HmacSignatureVerifierTest {

    private static final long NOW = 1_800_000_000L;
    private static final byte[] BODY = "{\"id\":\"evt_1\",\"type\":\"deposit.succeeded\"}".getBytes(StandardCharsets.UTF_8);

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private final HmacSignatureVerifier verifier =
            new HmacSignatureVerifier(Arrays.asList("current-secret", "old-secret"), Duration.ofMinutes(5), clock);

    @Test
    void matchesIndependentlyComputedVectors() {
        // printf '1.{}' | openssl dgst -sha256 -hmac key
        assertThat(HmacSignatureVerifier.sign("key", 1, "{}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("1ba6b8171186efc613e8bcc0cbdab2748f24984d7c5a84faa2637afa0e40d224");
        // printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac secret   (same as scripts/sign-webhook.sh)
        assertThat(HmacSignatureVerifier.sign("secret", 1_700_000_000L, "{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("49f24e537407743fa4a0242bb63b94b9a47ee99cbbe071ccd8a22550ae411686");
    }

    @Test
    void validSignatureWithCurrentSecret() {
        assertThat(verifier.verify(Long.toString(NOW), sign("current-secret", NOW, BODY), BODY)).isEqualTo(Result.VALID);
    }

    @Test
    void previousSecretIsAcceptedDuringRotation() {
        assertThat(verifier.verify(Long.toString(NOW), sign("old-secret", NOW, BODY), BODY)).isEqualTo(Result.VALID);
    }

    @Test
    void unknownSecretIsRejected() {
        assertThat(verifier.verify(Long.toString(NOW), sign("attacker-guess", NOW, BODY), BODY))
                .isEqualTo(Result.INVALID_SIGNATURE);
    }

    @Test
    void tamperedBodyIsRejected() {
        String signature = sign("current-secret", NOW, BODY);
        byte[] tampered = new String(BODY, StandardCharsets.UTF_8).replace("evt_1", "evt_2").getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(Long.toString(NOW), signature, tampered)).isEqualTo(Result.INVALID_SIGNATURE);
    }

    @Test
    void evenWhitespaceChangesBreakTheSignature() {
        String signature = sign("current-secret", NOW, BODY);
        byte[] reformatted = new String(BODY, StandardCharsets.UTF_8).replace(",", ", ").getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(Long.toString(NOW), signature, reformatted)).isEqualTo(Result.INVALID_SIGNATURE);
    }

    @Test
    void changingTheTimestampBreaksTheSignature() {
        String signature = sign("current-secret", NOW, BODY);

        assertThat(verifier.verify(Long.toString(NOW + 1), signature, BODY)).isEqualTo(Result.INVALID_SIGNATURE);
    }

    @Test
    void staleOrFutureTimestampBeyondFiveMinutesIsRejected() {
        long stale = NOW - 301;
        long future = NOW + 301;

        assertThat(verifier.verify(Long.toString(stale), sign("current-secret", stale, BODY), BODY))
                .isEqualTo(Result.STALE_TIMESTAMP);
        assertThat(verifier.verify(Long.toString(future), sign("current-secret", future, BODY), BODY))
                .isEqualTo(Result.STALE_TIMESTAMP);
        long edge = NOW - 300;
        assertThat(verifier.verify(Long.toString(edge), sign("current-secret", edge, BODY), BODY))
                .isEqualTo(Result.VALID);
    }

    @Test
    void malformedHeadersAreRejected() {
        String signature = sign("current-secret", NOW, BODY);

        assertThat(verifier.verify(null, signature, BODY)).isEqualTo(Result.MALFORMED);
        assertThat(verifier.verify(Long.toString(NOW), null, BODY)).isEqualTo(Result.MALFORMED);
        assertThat(verifier.verify("yesterday", signature, BODY)).isEqualTo(Result.MALFORMED);
        assertThat(verifier.verify(Long.toString(NOW), "not-hex!", BODY)).isEqualTo(Result.MALFORMED);
    }

    @Test
    void signatureHexIsCaseInsensitive() {
        assertThat(verifier.verify(Long.toString(NOW), sign("current-secret", NOW, BODY).toUpperCase(), BODY))
                .isEqualTo(Result.VALID);
    }

    @Test
    void requiresAtLeastOneSecret() {
        assertThatThrownBy(() -> new HmacSignatureVerifier(List.of(" "), Duration.ofMinutes(5), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String sign(String secret, long timestamp, byte[] body) {
        return HmacSignatureVerifier.sign(secret, timestamp, body);
    }
}
