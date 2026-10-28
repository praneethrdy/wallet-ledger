package com.example.wallet.common.error;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.example.wallet.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Builds the API's ProblemDetail shape outside of Spring MVC's exception handling: in servlet
 * filters and security handlers (which run before any controller), and when the idempotency
 * layer stores a 4xx response for replay.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        if (path != null) {
            try {
                problem.setInstance(URI.create(path));
            } catch (IllegalArgumentException ignored) {
                // leave instance unset for paths that aren't valid URIs
            }
        }
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        return problem;
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper,
                             ErrorCode code, String detail) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), of(code, detail, request.getRequestURI()));
    }
}
