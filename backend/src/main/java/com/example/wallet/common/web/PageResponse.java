package com.example.wallet.common.web;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a keyset-paginated list. {@code nextCursor} is null on the last page.
 * Pass it back as {@code ?cursor=} to get the next page.
 */
public record PageResponse<T>(List<T> items, String nextCursor) {

    /**
     * Callers fetch {@code limit + 1} rows: if the extra row exists there is another page,
     * and the cursor points at the last row actually returned.
     */
    public static <T> PageResponse<T> of(List<T> rowsPlusOne, int limit, Function<T, String> cursorOf) {
        if (rowsPlusOne.size() <= limit) {
            return new PageResponse<>(rowsPlusOne, null);
        }
        List<T> page = rowsPlusOne.subList(0, limit);
        return new PageResponse<>(List.copyOf(page), cursorOf.apply(page.get(limit - 1)));
    }
}
