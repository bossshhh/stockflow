package com.stockflow.inventory;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

/** An adjustment would push on-hand stock above the per-product ceiling. */
public class StockLimitExceededException extends ApiException {

    public StockLimitExceededException(long productId, int onHand, long maxOnHand) {
        super(ErrorCode.STOCK_LIMIT_EXCEEDED, "Cannot add stock to product " + productId + ": on hand " + onHand
                + ", at most " + maxOnHand + " allowed");
    }
}
