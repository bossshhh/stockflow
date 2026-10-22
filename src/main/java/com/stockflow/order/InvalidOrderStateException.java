package com.stockflow.order;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class InvalidOrderStateException extends ApiException {

    public InvalidOrderStateException(Long orderId, OrderStatus current, String action) {
        super(ErrorCode.INVALID_ORDER_STATE, "Cannot " + action + " order " + orderId + " because it is " + current);
    }
}
