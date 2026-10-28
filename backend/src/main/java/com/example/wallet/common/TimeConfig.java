package com.example.wallet.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WHY inject a Clock instead of calling Instant.now(): time-dependent rules (token expiry,
 * 24h idempotency TTL, "PENDING deposit older than 24h", daily balance buckets) become
 * testable. A test swaps in a fixed or movable clock instead of sleeping or hoping the test
 * doesn't run across midnight. Always UTC; time zones are applied only when presenting dates.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
