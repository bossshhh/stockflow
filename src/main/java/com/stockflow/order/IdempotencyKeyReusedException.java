package com.stockflow.order;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

/** The same idempotency key arrived with a different order: a client bug, so refuse instead of guessing. */
public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "This Idempotency-Key was already used for a different order; send a new key for a new order");
    }
}
