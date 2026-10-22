package com.stockflow.security;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

/** One message for unknown email, wrong password and disabled account, so attackers learn nothing. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
    }
}
