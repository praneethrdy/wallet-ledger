package com.example.wallet.auth.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.wallet.testsupport.AbstractIntegrationTest;

/**
 * Architecture guard: every /api/v1 endpoint must declare WHO may call it, via one of the
 * capability annotations (anything carrying @PreAuthorize). Forgetting it would silently
 * make an endpoint available to every logged-in user, so the build fails instead.
 *
 * <p>Exempt: /auth (public by design), /me (any user, about themselves) and the webhook
 * (authenticated by HMAC signature, not by a user).
 */
class EndpointAccessRulesIT extends AbstractIntegrationTest {

    private static final List<String> EXEMPT_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/me",
            "/api/v1/webhooks/");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyApiEndpointDeclaresAnAccessRule() {
        List<String> seen = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            for (String pattern : entry.getKey().getPatternValues()) {
                seen.add(pattern);
                boolean api = pattern.startsWith("/api/v1/");
                boolean exempt = EXEMPT_PREFIXES.stream().anyMatch(pattern::startsWith);
                if (api && !exempt && !declaresAccessRule(handler)) {
                    missing.add(entry.getKey().getMethodsCondition() + " " + pattern + " -> " + handler);
                }
            }
        }

        assertThat(seen).as("sanity check: the scan must see the real endpoints").contains("/api/v1/me");
        assertThat(missing).as("endpoints without a capability annotation").isEmpty();
    }

    private static boolean declaresAccessRule(HandlerMethod handler) {
        return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);
    }
}
