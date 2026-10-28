package com.example.wallet.auth.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.testsupport.AbstractIntegrationTest;
import com.example.wallet.testsupport.TestUsers;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.example.wallet.user.Role;

/**
 * The spec's RBAC matrix, checked against each capability annotation with real JWTs.
 * Real endpoints (wallets, transfers, reversal, support) get their own RBAC tests as they are
 * built; this test pins down the annotations themselves.
 */
@AutoConfigureMockMvc
@Import(RbacMatrixIT.ProbeController.class)
class RbacMatrixIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers testUsers;

    private final Map<Role, TestUser> users = new EnumMap<>(Role.class);

    @BeforeEach
    void createOneUserPerRole() {
        for (Role role : Role.values()) {
            users.put(role, testUsers.create(role));
        }
    }

    @ParameterizedTest(name = "{0} {1}: CUSTOMER={2} SUPPORT={3} ADMIN={4}")
    @CsvSource({
            // method, path,                        CUSTOMER, SUPPORT, ADMIN
            "GET,  /test-probe/can-hold-wallets,     200,      403,     200",
            "POST, /test-probe/can-move-money,       200,      403,     403",
            "GET,  /test-probe/can-view-any-user,    403,      200,     200",
            "POST, /test-probe/admin-only,           403,      403,     200",
            "GET,  /test-probe/any-authenticated,    200,      200,     200",
    })
    void eachRoleGetsExactlyTheAccessTheMatrixAllows(String method, String path,
                                                     int customer, int support, int admin) throws Exception {
        expect(Role.CUSTOMER, method, path, customer);
        expect(Role.SUPPORT, method, path, support);
        expect(Role.ADMIN, method, path, admin);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,  /test-probe/can-hold-wallets",
            "POST, /test-probe/can-move-money",
            "GET,  /test-probe/can-view-any-user",
            "POST, /test-probe/admin-only",
            "GET,  /test-probe/any-authenticated",
    })
    void anonymousCallersGet401(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void expect(Role role, String method, String path, int expectedStatus) throws Exception {
        var result = mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", users.get(role).bearer()))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 403) {
            // Must be our ProblemDetail, not a 500 from the catch-all handler.
            result.andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/test-probe/can-hold-wallets")
        @CanHoldWallets
        String canHoldWallets() {
            return "ok";
        }

        @PostMapping("/test-probe/can-move-money")
        @CanMoveMoney
        String canMoveMoney() {
            return "ok";
        }

        @GetMapping("/test-probe/can-view-any-user")
        @CanViewAnyUser
        String canViewAnyUser() {
            return "ok";
        }

        @PostMapping("/test-probe/admin-only")
        @AdminOnly
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/test-probe/any-authenticated")
        String anyAuthenticated() {
            return "ok";
        }
    }
}
