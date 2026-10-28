package com.example.wallet.auth;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code security.auth.*}. The JWT secret comes only from the environment (JWT_SECRET) in prod.
 *
 * @param jwtSecret       base64-encoded HMAC key, at least 256 bits (HS256 requires it)
 * @param issuer          value of the {@code iss} claim; tokens from any other issuer are rejected
 * @param accessTokenTtl  lifetime of access JWTs
 * @param refreshTokenTtl lifetime of refresh tokens
 */
@ConfigurationProperties(prefix = "security.auth")
public record AuthProperties(String jwtSecret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("security.auth.jwt-secret (env JWT_SECRET) is required");
        }
        if (Base64.getDecoder().decode(jwtSecret).length < 32) {
            // Message deliberately does not echo the value.
            throw new IllegalStateException("security.auth.jwt-secret must decode to at least 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "wallet-backend";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(7);
        }
    }

    public byte[] jwtSecretBytes() {
        return Base64.getDecoder().decode(jwtSecret);
    }
}
