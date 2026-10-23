package com.stockflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. Clients branch on these, so never rename one; add new ones instead.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_SKU(HttpStatus.CONFLICT),

    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    STOCK_LIMIT_EXCEEDED(HttpStatus.CONFLICT),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT),
    CANCEL_WINDOW_EXPIRED(HttpStatus.CONFLICT),
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
