package com.stockflow.product;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(long id) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "Product " + id + " was not found");
    }
}
