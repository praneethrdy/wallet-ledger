package com.example.wallet.common.web;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.wallet.common.error.ErrorCode;
import com.example.wallet.common.error.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Caps request bodies (default 64 KiB; every legitimate body here is under 1 KiB).
 *
 * <p>Two layers: a declared {@code Content-Length} over the limit is refused up front with 413.
 * A chunked request has no Content-Length, so the body stream is also wrapped and throws
 * {@link PayloadTooLargeException} the moment it exceeds the limit. GlobalExceptionHandler
 * turns that into the same 413. Without the second layer, a client could stream an endless
 * body into Jackson's buffer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // right after the correlation id
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(@Value("${app.http.max-request-bytes:65536}") long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            ProblemDetails.write(request, response, objectMapper, ErrorCode.PAYLOAD_TOO_LARGE,
                    "Request body exceeds " + maxBytes + " bytes");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** Thrown while reading a body that is larger than allowed. */
    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(long maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = delegate.read(buffer, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws PayloadTooLargeException {
            read += n;
            if (read > maxBytes) {
                throw new PayloadTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
