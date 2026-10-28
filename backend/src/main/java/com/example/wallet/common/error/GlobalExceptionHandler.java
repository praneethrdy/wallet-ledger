package com.example.wallet.common.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.wallet.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import jakarta.validation.ConstraintViolationException;

/**
 * Turns every exception into an RFC 7807 ProblemDetail with a stable {@code code}:
 * <pre>
 * { "type": "about:blank", "title": "Insufficient funds", "status": 422,
 *   "detail": "...", "instance": "/api/v1/transfers",
 *   "code": "INSUFFICIENT_FUNDS", "correlationId": "…", "errors": [ … ] }
 * </pre>
 * Extending ResponseEntityExceptionHandler covers Spring's own exceptions (404, 405, 415,
 * bad JSON, ...). All paths go through {@link #handleExceptionInternal}, so decoration
 * (code, instance, correlationId) and the "never leak internals on 5xx" rule live in one place.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE = "code";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        log.debug("Business error {}: {}", ex.code(), ex.getMessage());
        ProblemDetail body = problem(ex.code(), ex.getMessage());
        return handleExceptionInternal(ex, body, new HttpHeaders(), ex.code().status(), request);
    }

    /** Validation of @Validated service/controller parameters outside Spring MVC's binding. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationFailed(ex, errors, request);
    }

    /**
     * @PreAuthorize failures are thrown inside the controller call, so they reach this advice
     * instead of Spring Security's filter. Without this handler the catch-all below would turn
     * every "forbidden" into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
        return handleExceptionInternal(ex, body, new HttpHeaders(), ErrorCode.FORBIDDEN.status(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.UNAUTHORIZED, "Authentication required");
        return handleExceptionInternal(ex, body, new HttpHeaders(), ErrorCode.UNAUTHORIZED.status(), request);
    }

    /** Anything unexpected is a bug: log it in full, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.INTERNAL_ERROR, null);
        return handleExceptionInternal(ex, body, new HttpHeaders(), ErrorCode.INTERNAL_ERROR.status(), request);
    }

    // ---- Spring MVC exceptions -----------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.add(new FieldViolation(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new FieldViolation(ge.getObjectName(), ge.getDefaultMessage())));
        return validationFailed(ex, errors, request);
    }

    /** A missing Idempotency-Key header gets its own code; other missing headers/params are 400s. */
    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing
                && "Idempotency-Key".equalsIgnoreCase(missing.getHeaderName())) {
            ProblemDetail body = problem(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Send an Idempotency-Key header (e.g. a fresh UUID) with every mutating request");
            return handleExceptionInternal(ex, body, headers, ErrorCode.IDEMPOTENCY_KEY_REQUIRED.status(), request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    /** Constraints on @RequestParam / @PathVariable / @RequestHeader (Spring 6.1+ method validation). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(err -> errors.add(new FieldViolation(name, err.getDefaultMessage())));
        });
        return validationFailed(ex, errors, request);
    }

    /**
     * Bad JSON. Jackson's own messages include Java class names, so we never pass them through;
     * we only report the JSON path of the offending field.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException upe) {
            errors.add(new FieldViolation(jsonPath(upe), "unknown field"));
        } else if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            errors.add(new FieldViolation(jsonPath(mie), "invalid value"));
        }
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Malformed or invalid request body");
        if (!errors.isEmpty()) {
            body.setProperty("errors", errors);
        }
        return handleExceptionInternal(ex, body, headers, ErrorCode.VALIDATION_FAILED.status(), request);
    }

    /** Single exit point for every error response. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd
                ? pd
                : ProblemDetail.forStatus(statusCode);

        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            ErrorCode code = ErrorCode.fromStatus(statusCode.value());
            problem.setProperty(CODE, code.name());
            if (problem.getTitle() == null) {
                problem.setTitle(code.title());
            }
        }
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled exception", ex);
            // Framework 5xx details can mention internals; replace them.
            problem.setDetail("An unexpected error occurred. Quote the correlationId when contacting support.");
        }
        if (request instanceof ServletWebRequest swr) {
            problem.setInstance(safeUri(swr.getRequest().getRequestURI()));
        }
        problem.setProperty("correlationId", CorrelationIdFilter.current());

        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResponseEntity<Object> validationFailed(Exception ex, List<FieldViolation> errors, WebRequest request) {
        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, new HttpHeaders(), ErrorCode.VALIDATION_FAILED.status(), request);
    }

    private static ProblemDetail problem(ErrorCode code, @Nullable String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(code.title());
        pd.setProperty(CODE, code.name());
        return pd;
    }

    /** e.g. "items[0].amount" */
    private static String jsonPath(JsonMappingException ex) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /** "transfer.request.amount" -> "amount" */
    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
    }

    @Nullable
    private static URI safeUri(String path) {
        try {
            return URI.create(path);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
