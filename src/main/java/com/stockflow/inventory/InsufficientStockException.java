package com.stockflow.inventory;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
