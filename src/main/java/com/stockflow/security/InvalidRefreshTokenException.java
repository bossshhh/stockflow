package com.stockflow.security;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

/** One message for missing, unknown, expired, revoked and reused tokens: the client simply signs in again. */
public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN, "Session expired; please sign in again");
    }
}
