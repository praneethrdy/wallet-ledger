package com.example.wallet.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.example.wallet.user.User;

/** Mints access JWTs and opaque refresh tokens. Holds no state. */
@Component
class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JwtEncoder jwtEncoder;
    private final AuthProperties props;
    private final Clock clock;

    TokenService(JwtEncoder jwtEncoder, AuthProperties props, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.clock = clock;
    }

    /** Short-lived, self-contained: the API verifies it without a DB lookup. */
    String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString()) // jti: unique per token, useful for audit/denylist
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    /**
     * 256 random bits, base64url (43 chars). Opaque on purpose: unlike a JWT it carries no
     * information and is only valid while its hash exists (and is not revoked) in our DB.
     */
    static String newRefreshTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /**
     * Plain SHA-256 is right here (unlike passwords): the input is 256 random bits, so there is
     * nothing to brute-force, and we need a deterministic hash to look the token up.
     */
    static byte[] hash(String refreshTokenValue) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(refreshTokenValue.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
