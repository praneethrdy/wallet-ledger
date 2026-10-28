package com.example.wallet.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an ID that appears in every log line (via MDC), in the response header,
 * and in error bodies. A user reporting "my transfer failed" can quote one ID, and support can
 * find every log line for that request.
 *
 * <p>Runs first (before Spring Security, added in Step 3) so even auth failures are tagged.
 * A client-supplied {@code X-Request-Id} is reused only if it is short and plain: a value with
 * CR/LF could forge log lines or inject response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id); // set before the body is written, or it would be too late
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled: without this, the next request on this thread
            // would log under our ID.
            MDC.remove(MDC_KEY);
        }
    }

    /** Current request's ID, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
