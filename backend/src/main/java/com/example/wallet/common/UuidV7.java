package com.example.wallet.common;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: 48-bit Unix-millisecond timestamp, then random bits.
 *
 * <p>WHY v7 over random v4 for primary keys: v4 values land at random places in the B-tree
 * index, so every insert touches a different page. v7 values grow with time, so inserts append
 * to the right edge of the index. That is faster and the IDs sort by creation time. The 74
 * random bits still make IDs unguessable, which sequential integers are not (rule 10).
 *
 * <p>Layout: | unix_ts_ms (48) | ver=7 (4) | rand_a (12) | var=10 (2) | rand_b (62) |
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate(Clock clock) {
        long millis = clock.millis();
        long msb = (millis << 16)                       // timestamp in the top 48 bits
                | 0x7000L                               // version 7
                | (RANDOM.nextInt() & 0x0FFFL);          // rand_a
        long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL)
                | 0x8000_0000_0000_0000L;               // variant 10
        return new UUID(msb, lsb);
    }

    /** The millisecond timestamp embedded in a v7 UUID. */
    public static long timestampMillis(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
