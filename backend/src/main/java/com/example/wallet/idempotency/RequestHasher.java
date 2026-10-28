package com.example.wallet.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Fingerprint of a request: SHA-256 over method, path and the CANONICAL JSON of the parsed
 * body. Canonical (keys sorted, no whitespace) means {"a":1,"b":2} and { "b": 2, "a": 1 }
 * hash the same: a client retrying the same intent isn't punished for re-serializing it.
 */
@Component
public class RequestHasher {

    private final ObjectMapper canonical;

    @Autowired
    @SuppressWarnings("deprecation") // configure(MapperFeature) is the only way on a plain ObjectMapper copy
    public RequestHasher(ObjectMapper objectMapper) {
        this.canonical = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    /** For unit tests. */
    RequestHasher() {
        this(JsonMapper.builder().findAndAddModules().build());
    }

    public String hash(String method, String path, Object body) {
        try {
            String json = body == null ? "" : canonical.writeValueAsString(body);
            String input = method.toUpperCase(Locale.ROOT) + "\n" + path + "\n" + json;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request body is not serializable", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
