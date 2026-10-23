package com.stockflow.common.exception;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception into an {@link ApiError}. Messages never echo raw client input or internal
 * details; unexpected errors are logged with their stack trace and returned as a generic 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "1";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Request {} rejected: {}", request.getRequestURI(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, violations);
    }

    /**
     * Constraint annotations on @RequestParam / @PathVariable / @RequestHeader values, e.g.
     * {@code @Min(0) int threshold}. Once a handler has such a constraint, Spring also reports its
     * {@code @Valid @RequestBody} errors here (not as MethodArgumentNotValidException), so body errors keep
     * their field paths ("items[0].quantity"), exactly as in {@link #handleValidation}.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleParameterValidation(HandlerMethodValidationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::violations)
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, violations);
    }

    private static Stream<ApiError.FieldViolation> violations(ParameterValidationResult result) {
        if (result instanceof ParameterErrors bodyErrors) {
            return bodyErrors.getFieldErrors().stream()
                    .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()));
        }
        return result.getResolvableErrors().stream()
                .map(error -> new ApiError.FieldViolation(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_PARAMETER, "Parameter '" + ex.getName() + "' has an invalid value", request, List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(ErrorCode.INVALID_PARAMETER, "Parameter '" + ex.getParameterName() + "' is required", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint matches this path", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported here", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported; use application/json", request, List.of());
    }

    /**
     * The database cannot be reached (down, restarting, pool exhausted). Temporary by nature, so 503 tells
     * clients they may retry; the cause is logged, never returned.
     */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiError> handleDatabaseUnavailable(Exception ex, HttpServletRequest request) {
        log.warn("Database unavailable on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.SERVICE_UNAVAILABLE, "The service is temporarily unavailable; please retry shortly",
                request, List.of());
    }

    /**
     * A lock could not be taken in time (lock_timeout) or PostgreSQL broke a deadlock. Temporary: the same
     * request will most likely succeed a moment later, and the whole transaction was rolled back, so a
     * retry is safe (for placing orders, use the same Idempotency-Key). Hence 409 with Retry-After.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleLockConflict(PessimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        ResponseEntity<ApiError> response = build(ErrorCode.CONCURRENT_UPDATE,
                "Another request is changing the same data right now; please retry", request, List.of());
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request,
                                           List<ApiError.FieldViolation> fieldErrors) {
        ApiError body = new ApiError(clock.instant(), code.status().value(), code.name(), message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(code.status()).body(body);
    }
}
