package com.stockflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.stockflow.order.OrderStatus;

/**
 * Orders created in [from, to).
 *
 * @param ordersByStatus   every status is present, with 0 when there were none
 * @param confirmedRevenue total of CONFIRMED orders only: pending orders are not sales yet, cancelled ones no longer
 */
public record OrderSummaryReport(
        Instant from,
        Instant to,
        long totalOrders,
        Map<OrderStatus, Long> ordersByStatus,
        BigDecimal confirmedRevenue) {
}
