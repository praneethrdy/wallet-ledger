package com.example.wallet.common.web;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;

/**
 * Position in a list ordered by {@code (created_at DESC, id DESC)}.
 *
 * <p>WHY keyset instead of OFFSET: {@code OFFSET 10000} makes Postgres read and discard 10,000
 * rows, and if new rows arrive between page requests, OFFSET pages shift and the user sees
 * duplicates or misses rows. "Give me rows older than (t, id)" uses the index directly and
 * stays stable while new rows are inserted at the top.
 *
 * <p>The cursor is opaque to clients (base64url); they must not build or parse it.
 */
public record KeysetCursor(Instant createdAt, String id) {

    public String encode() {
        String raw = createdAt.getEpochSecond() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Timestamp createdAtTimestamp() {
        return Timestamp.from(createdAt);
    }

    /** Null in, null out; anything malformed is a 400. */
    public static KeysetCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant at = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            if (parts[2].isBlank()) {
                throw new IllegalArgumentException("empty id");
            }
            return new KeysetCursor(at, parts[2]);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
}
