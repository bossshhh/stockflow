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
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_SKU(HttpStatus.CONFLICT),

    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT),
    CANCEL_WINDOW_EXPIRED(HttpStatus.CONFLICT),
    PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
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
