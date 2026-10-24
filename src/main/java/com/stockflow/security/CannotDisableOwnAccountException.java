package com.stockflow.security;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class CannotDisableOwnAccountException extends ApiException {

    public CannotDisableOwnAccountException() {
        super(ErrorCode.CANNOT_DISABLE_OWN_ACCOUNT, "You cannot disable your own account; ask another admin");
    }
}
