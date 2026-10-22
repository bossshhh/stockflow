package com.stockflow.common.exception;

public class InvalidRequestParameterException extends ApiException {

    public InvalidRequestParameterException(String message) {
        super(ErrorCode.INVALID_PARAMETER, message);
    }
}
