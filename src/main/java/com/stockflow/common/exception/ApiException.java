package com.stockflow.common.exception;

/**
 * Base class for every expected business error. The {@link GlobalExceptionHandler} turns it into an
 * {@link ApiError} with the status that belongs to its {@link ErrorCode}.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
