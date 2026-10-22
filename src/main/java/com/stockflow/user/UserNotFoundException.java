package com.stockflow.user;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(long id) {
        super(ErrorCode.USER_NOT_FOUND, "User " + id + " was not found");
    }
}
