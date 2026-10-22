package com.stockflow.product;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class DuplicateSkuException extends ApiException {

    public DuplicateSkuException(String sku) {
        super(ErrorCode.DUPLICATE_SKU, "A product with SKU " + sku + " already exists");
    }
}
