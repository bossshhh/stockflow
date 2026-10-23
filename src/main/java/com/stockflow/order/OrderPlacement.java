package com.stockflow.order;

import com.stockflow.order.dto.OrderResponse;

/**
 * @param replayed true when the order was created by an earlier request with the same idempotency key
 */
public record OrderPlacement(OrderResponse order, boolean replayed) {
}
