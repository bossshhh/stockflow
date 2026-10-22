package com.stockflow.order;

import java.time.Duration;

import com.stockflow.common.exception.ApiException;
import com.stockflow.common.exception.ErrorCode;

public class CancelWindowExpiredException extends ApiException {

    public CancelWindowExpiredException(Long orderId, Duration window) {
        super(ErrorCode.CANCEL_WINDOW_EXPIRED, "Order " + orderId + " can no longer be cancelled: confirmed orders "
                + "can only be cancelled within " + window.toMinutes() + " minutes of confirmation");
    }
}
