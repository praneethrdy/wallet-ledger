package com.example.wallet.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestHasherTest {

    private final RequestHasher hasher = new RequestHasher();

    record Body(String b, long a) {
    }

    @Test
    void sameRequestSameHash() {
        assertThat(hasher.hash("POST", "/api/v1/transfers", new Body("x", 1)))
                .isEqualTo(hasher.hash("post", "/api/v1/transfers", new Body("x", 1)))
                .hasSize(64)
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    void fieldOrderDoesNotMatter() {
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("a", 1);
        ab.put("b", Map.of("y", 2, "x", 1));
        Map<String, Object> ba = new LinkedHashMap<>();
        ba.put("b", Map.of("x", 1, "y", 2));
        ba.put("a", 1);

        assertThat(hasher.hash("POST", "/p", ab)).isEqualTo(hasher.hash("POST", "/p", ba));
    }

    @Test
    void anyDifferenceInMethodPathOrBodyChangesTheHash() {
        String base = hasher.hash("POST", "/api/v1/transfers", new Body("x", 1));

        assertThat(hasher.hash("PUT", "/api/v1/transfers", new Body("x", 1))).isNotEqualTo(base);
        assertThat(hasher.hash("POST", "/api/v1/deposits", new Body("x", 1))).isNotEqualTo(base);
        assertThat(hasher.hash("POST", "/api/v1/transfers", new Body("x", 2))).isNotEqualTo(base);
        assertThat(hasher.hash("POST", "/api/v1/transfers", null)).isNotEqualTo(base);
    }
}
