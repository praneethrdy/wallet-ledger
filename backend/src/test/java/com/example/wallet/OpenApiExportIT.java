package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Exports the live OpenAPI document to backend/openapi.json: the contract the frontend is built
 * against. Keys are sorted so the file only changes when the API changes; CI fails if the
 * committed file is out of date (git diff --exit-code backend/openapi.json).
 */
@AutoConfigureMockMvc
class OpenApiExportIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exportsTheContract() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode spec = mapper.readTree(json);

        // Sanity checks on the contract itself.
        assertThat(spec.path("paths").has("/api/v1/transfers")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/webhooks/payment-provider")).isTrue();
        assertThat(spec.path("paths").has("/dev/mock-provider/deposits/{providerReference}/complete")).isFalse();
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(spec.path("components").path("schemas").has("Problem")).isTrue();
        JsonNode transferParams = spec.path("paths").path("/api/v1/transfers").path("post").path("parameters");
        assertThat(transferParams.findValuesAsText("name")).contains("Idempotency-Key");
        assertThat(spec.path("paths").path("/api/v1/auth/login").path("post").path("security")).isEmpty();
        // The caller comes from the JWT; the contract must never ask the client to send it.
        assertThat(spec.path("paths").findValuesAsText("name")).doesNotContain("caller");
        assertThat(spec.path("components").path("schemas").has("AuthUser")).isFalse();
        assertThat(spec.path("components").path("schemas").has("JsonNode")).isFalse();

        Object sorted = mapper.convertValue(spec, Map.class);
        String pretty = mapper.writeValueAsString(sorted) + "\n";
        Files.writeString(target(), pretty);
    }

    // Maven runs from backend/, IDEs sometimes from the repo root.
    private static Path target() {
        return Files.exists(Path.of("pom.xml")) ? Path.of("openapi.json") : Path.of("backend", "openapi.json");
    }
}
