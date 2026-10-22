package com.stockflow.order;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(long id) {
        super(ErrorCode.ORDER_NOT_FOUND, "Order " + id + " was not found");
    }
}
