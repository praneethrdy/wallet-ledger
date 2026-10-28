package com.example.wallet.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.common.Money;
import com.example.wallet.common.web.CorrelationIdFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Web-layer slice test (no DB, no Docker): the error contract and the correlation ID.
 * A tiny probe controller throws each kind of error so we can check the response shape.
 * Security is excluded here on purpose: 401/403 responses are covered by AuthIT.
 */
@WebMvcTest(controllers = ErrorHandlingTest.ProbeController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class})
@Import(ErrorHandlingTest.ProbeController.class)
class ErrorHandlingTest {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    MockMvc mvc;

    // ---- business errors -----------------------------------------------------------------

    @Test
    void apiExceptionBecomesProblemDetailWithStableCode() throws Exception {
        mvc.perform(get("/probe/api-error/INSUFFICIENT_FUNDS"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Insufficient funds"))
                .andExpect(jsonPath("$.detail").value("probe detail"))
                .andExpect(jsonPath("$.instance").value("/probe/api-error/INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void eachCodeUsesItsOwnStatus() throws Exception {
        mvc.perform(get("/probe/api-error/IDEMPOTENCY_REQUEST_IN_PROGRESS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
        mvc.perform(get("/probe/api-error/NOT_FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void currencyMismatchFromMoneyIs422() throws Exception {
        mvc.perform(get("/probe/currency-mismatch"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    // ---- validation ----------------------------------------------------------------------

    @Test
    void beanValidationListsEveryFieldError() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
    }

    @Test
    void unknownJsonFieldIsRejectedAndNamed() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"amount\":5,\"ammount\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("ammount"))
                .andExpect(jsonPath("$.errors[0].message").value("unknown field"))
                .andExpect(content().string(not(containsString("com.example"))));
    }

    @Test
    void wrongJsonTypeIsRejectedWithoutLeakingJavaTypes() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"amount\":\"lots\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("long"))));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void requestParamConstraintsAreValidationErrors() throws Exception {
        mvc.perform(get("/probe/page").param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
    }

    // ---- framework errors ----------------------------------------------------------------

    @Test
    void unknownPathIs404WithCode() throws Exception {
        mvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodIs405WithCode() throws Exception {
        mvc.perform(get("/probe/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeIs415WithCode() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- unexpected errors never leak ----------------------------------------------------

    @Test
    void unexpectedExceptionIsGeneric500WithoutInternals() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.example"))));
    }

    // ---- correlation ID ------------------------------------------------------------------

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        mvc.perform(get("/probe/ok"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void echoesSafeClientCorrelationIdInHeaderAndErrorBody() throws Exception {
        mvc.perform(get("/probe/api-error/NOT_FOUND").header(CorrelationIdFilter.HEADER, "client-req.42_A"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-req.42_A"))
                .andExpect(jsonPath("$.correlationId").value("client-req.42_A"));
    }

    @Test
    void replacesUnsafeClientCorrelationId() throws Exception {
        // A CR/LF could forge log lines; an overlong value could bloat every log entry.
        mvc.perform(get("/probe/ok").header(CorrelationIdFilter.HEADER, "abc\r\nX-Injected: 1"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
        mvc.perform(get("/probe/ok").header(CorrelationIdFilter.HEADER, "a".repeat(65)))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void correlationIdIsVisibleToHandlerCodeViaMdc() throws Exception {
        mvc.perform(get("/probe/correlation").header(CorrelationIdFilter.HEADER, "trace-me"))
                .andExpect(content().string("trace-me"));
    }

    // ---- probe controller ----------------------------------------------------------------

    record ProbeRequest(@NotBlank String name, @Positive long amount) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/ok")
        String ok() {
            return "ok";
        }

        @GetMapping("/probe/correlation")
        String correlation() {
            return CorrelationIdFilter.current();
        }

        @GetMapping("/probe/api-error/{code}")
        String apiError(@PathVariable String code) {
            throw new ApiException(ErrorCode.valueOf(code), "probe detail");
        }

        @GetMapping("/probe/currency-mismatch")
        Money currencyMismatch() {
            return Money.of(1, "INR").plus(Money.of(1, "USD"));
        }

        @PostMapping("/probe/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }

        @GetMapping("/probe/page")
        String page(@RequestParam @Max(100) int limit) {
            return "ok";
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("secret: SELECT * FROM users WHERE password_hash = ...");
        }
    }
}
