package com.stockflow.order;

import java.math.BigDecimal;

/** One row of the "orders per status" aggregate query. Built by JPQL; not exposed by the API. */
public record OrderStatusTotal(OrderStatus status, Long orderCount, BigDecimal totalAmount) {
}
