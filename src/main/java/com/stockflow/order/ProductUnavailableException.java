package com.stockflow.order;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

/** The product exists but has been deactivated, so it cannot be ordered. */
public class ProductUnavailableException extends ApiException {

    public ProductUnavailableException(String sku) {
        super(ErrorCode.PRODUCT_UNAVAILABLE, "Product " + sku + " is no longer available");
    }
}
