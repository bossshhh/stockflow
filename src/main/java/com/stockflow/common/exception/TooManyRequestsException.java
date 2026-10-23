package com.stockflow.common.exception;

import java.time.Duration;

/** A rate limit was hit. {@link #getRetryAfter()} is how long until the next attempt is allowed. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
