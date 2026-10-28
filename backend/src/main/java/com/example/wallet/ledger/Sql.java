package com.example.wallet.ledger;

import java.util.Collections;

/** Small SQL-building helpers. Values are always bound as parameters, never concatenated. */
public final class Sql {

    private Sql() {
    }

    /** "?, ?, ?" for an IN (...) list of n bound parameters. */
    public static String placeholders(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("IN list needs at least one value");
        }
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    /** Escapes LIKE wildcards so user input matches literally. Use with ESCAPE '\'. */
    public static String likeContains(String term) {
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
