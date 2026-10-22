package com.stockflow.user;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException() {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
    }
}
