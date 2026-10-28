package com.example.wallet.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.wallet.auth.AuthUser;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * The OpenAPI contract the frontend is generated from (exported to backend/openapi.json).
 *
 * <p>Rather than repeating error annotations on every method, a customizer adds the standard
 * ProblemDetail responses to every operation: 400/500 everywhere, 401/403 on authenticated
 * endpoints, 404 when there's a path id, and 409/422 on idempotent mutations. The server URL
 * is fixed so the exported file is byte-identical between runs.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String PROBLEM = "Problem";
    static {
        // AuthUser is resolved from the verified JWT, never sent by the client; without this,
        // springdoc documents it as a required "caller" query parameter on every endpoint.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser.class);
        // Raw provider payloads (webhook events) are arbitrary JSON objects.
        SpringDocUtils.getConfig().replaceWithClass(JsonNode.class, Object.class);
    }

    private static final List<String> PUBLIC_PREFIXES = List.of("/api/v1/auth/register", "/api/v1/auth/login",
            "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/webhooks/");

    @Bean
    OpenAPI walletOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wallet & Double-Entry Ledger API")
                        .version("v1")
                        .description("""
                                Amounts are integers in minor units (paise for INR). Every mutating endpoint \
                                except /auth and the webhook requires an Idempotency-Key header. Errors are \
                                RFC 7807 problem+json with a stable `code`."""))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login (15 min)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OpenApiCustomizer standardResponses() {
        return openApi -> {
            // Registered here, after generation: springdoc replaces the schemas of the base OpenAPI bean.
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> customize(path, method, op)));
        };
    }

    private static void customize(String path, PathItem.HttpMethod method, Operation op) {
        {
            boolean isPublic = PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
            boolean mutating = method != PathItem.HttpMethod.GET;
            boolean idempotent = hasIdempotencyKey(op);

            if (isPublic) {
                op.setSecurity(List.of()); // no bearer token
            }
            add(op, "400", "Validation failed / malformed request (VALIDATION_FAILED"
                    + (idempotent ? ", IDEMPOTENCY_KEY_REQUIRED" : "") + ")");
            if (!isPublic || path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/refresh")) {
                add(op, "401", "Missing, invalid or expired credentials (UNAUTHORIZED)");
            }
            if (path.startsWith("/api/v1/webhooks/")) {
                add(op, "401", "Signature or timestamp invalid (INVALID_SIGNATURE)");
            }
            if (!isPublic) {
                add(op, "403", "Role not allowed (FORBIDDEN)");
            }
            if (path.contains("{")) {
                add(op, "404", "Not found, or not yours (NOT_FOUND)");
            }
            if (idempotent) {
                add(op, "409", "Same Idempotency-Key still in progress (IDEMPOTENCY_REQUEST_IN_PROGRESS) or conflict");
                add(op, "422", "Business rule refused it, or Idempotency-Key reused with a different body");
            } else if (mutating && path.equals("/api/v1/auth/register")) {
                add(op, "409", "Email already registered (EMAIL_ALREADY_REGISTERED)");
            }
            add(op, "413", "Request body too large (PAYLOAD_TOO_LARGE)");
            add(op, "500", "Unexpected error (INTERNAL_ERROR); quote the correlationId");
            if (op.getParameters() != null) {
                op.getParameters().stream().filter(p -> "Idempotency-Key".equals(p.getName()))
                        .forEach(OpenApiConfig::describeIdempotencyKey);
            }
        }
    }

    private static boolean hasIdempotencyKey(Operation op) {
        return op.getParameters() != null
                && op.getParameters().stream().anyMatch(p -> "Idempotency-Key".equals(p.getName()));
    }

    private static void describeIdempotencyKey(Parameter p) {
        p.setRequired(true);
        p.setDescription("Unique per intended operation (a UUID). Retrying with the same key and body replays "
                + "the original response with header Idempotent-Replayed: true. Remembered for 24h.");
    }

    private static void add(Operation op, String status, String description) {
        if (op.getResponses() == null || op.getResponses().containsKey(status)) {
            return;
        }
        op.getResponses().addApiResponse(status, new ApiResponse().description(description).content(new Content()
                .addMediaType("application/problem+json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM)))));
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemSchema() {
        Map<String, Schema> fieldError = new LinkedHashMap<>();
        fieldError.put("field", new StringSchema());
        fieldError.put("message", new StringSchema());

        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("type", new StringSchema().example("about:blank"));
        props.put("title", new StringSchema().example("Insufficient funds"));
        props.put("status", new IntegerSchema().example(422));
        props.put("detail", new StringSchema());
        props.put("instance", new StringSchema().example("/api/v1/transfers"));
        props.put("code", new StringSchema().description("Stable machine-readable error code; branch on this")
                .example("INSUFFICIENT_FUNDS"));
        props.put("correlationId", new StringSchema().description("Same as the X-Request-Id response header"));
        props.put("errors", new ArraySchema().items(new ObjectSchema().properties(fieldError))
                .description("Per-field problems (VALIDATION_FAILED only)"));
        return new ObjectSchema().properties(props).required(List.of("title", "status", "code"))
                .description("RFC 7807 problem details");
    }
}
