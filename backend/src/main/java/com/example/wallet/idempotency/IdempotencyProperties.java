package com.example.wallet.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code idempotency.*}
 *
 * @param ttl          how long a key is remembered (spec: 24h)
 * @param staleAfter   an IN_PROGRESS key older than this may be taken over (crashed request)
 * @param inFlightWait how long a duplicate waits for the original to finish before answering 409
 */
@ConfigurationProperties(prefix = "idempotency")
public record IdempotencyProperties(Duration ttl, Duration staleAfter, Duration inFlightWait) {

    public IdempotencyProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
        if (staleAfter == null) {
            staleAfter = Duration.ofSeconds(60);
        }
        if (inFlightWait == null) {
            inFlightWait = Duration.ofSeconds(5);
        }
    }
}
