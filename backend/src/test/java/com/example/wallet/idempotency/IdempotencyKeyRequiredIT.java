package com.example.wallet.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.wallet.testsupport.ApiTest;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.user.Role;

/**
 * Spec §9 test 5: every mutating endpoint answers 400 IDEMPOTENCY_KEY_REQUIRED without the
 * header. The guard test makes sure this list is complete: a new POST endpoint fails the
 * build until it's added here (and therefore until it's checked).
 */
class IdempotencyKeyRequiredIT extends ApiTest {

    private static final Set<String> EXEMPT_PREFIXES = Set.of("/api/v1/auth/", "/api/v1/webhooks/");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    static Stream<Arguments> mutatingEndpoints() {
        UUID any = UUID.randomUUID();
        return Stream.of(
                Arguments.of(Role.CUSTOMER, "/api/v1/wallets", Map.of("name", "x")),
                Arguments.of(Role.CUSTOMER, "/api/v1/transfers",
                        Map.of("fromWalletId", any, "toWalletId", any, "amount", 1, "currency", "INR")),
                Arguments.of(Role.CUSTOMER, "/api/v1/deposits",
                        Map.of("walletId", any, "amount", 1, "currency", "INR")),
                Arguments.of(Role.ADMIN, "/api/v1/admin/transactions/" + any + "/reverse", Map.of("reason", "x")),
                Arguments.of(Role.ADMIN, "/api/v1/admin/wallets/" + any + "/freeze", null),
                Arguments.of(Role.ADMIN, "/api/v1/admin/wallets/" + any + "/unfreeze", null)
        );
    }

    /** Path templates of the endpoints above, as registered in Spring MVC. */
    static final Set<String> COVERED = Set.of(
            "POST /api/v1/wallets",
            "POST /api/v1/transfers",
            "POST /api/v1/deposits",
            "POST /api/v1/admin/transactions/{id}/reverse",
            "POST /api/v1/admin/wallets/{id}/freeze",
            "POST /api/v1/admin/wallets/{id}/unfreeze");

    @ParameterizedTest(name = "{1}")
    @MethodSource("mutatingEndpoints")
    void missingKeyIs400(Role role, String path, Map<String, Object> body) throws Exception {
        TestUser user = users.create(role);

        api.post(user, path, body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void everyMutatingEndpointIsCovered() {
        Set<String> mutating = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                if (method == RequestMethod.GET || method == RequestMethod.HEAD || method == RequestMethod.OPTIONS) {
                    continue;
                }
                for (String pattern : info.getPatternValues()) {
                    if (pattern.startsWith("/api/v1/") && EXEMPT_PREFIXES.stream().noneMatch(pattern::startsWith)) {
                        mutating.add(method + " " + pattern);
                    }
                }
            }
        });

        assertThat(mutating).as("mutating endpoints vs. the ones this test checks").isEqualTo(new TreeSet<>(COVERED));
    }
}
