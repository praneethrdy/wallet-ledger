package com.example.wallet.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    private static Clock at(long epochMillis) {
        return Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.generate(Clock.systemUTC());

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheClockTimestamp() {
        long millis = Instant.parse("2026-10-27T10:15:30.123Z").toEpochMilli();

        assertThat(UuidV7.timestampMillis(UuidV7.generate(at(millis)))).isEqualTo(millis);
    }

    @Test
    void laterIdsSortAfterEarlierOnesAsText() {
        // Postgres orders uuid bytewise, which is the same as comparing the hex text.
        String earlier = UuidV7.generate(at(1_700_000_000_000L)).toString();
        String later = UuidV7.generate(at(1_700_000_000_001L)).toString();

        assertThat(earlier).isLessThan(later);
    }

    @Test
    void idsInTheSameMillisecondAreStillUnique() {
        Clock frozen = at(1_700_000_000_000L);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7.generate(frozen));
        }
        assertThat(ids).hasSize(10_000);
    }
}
