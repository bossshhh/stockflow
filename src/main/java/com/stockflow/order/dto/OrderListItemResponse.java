package com.stockflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stockflow.order.CustomerOrder;
import com.stockflow.order.OrderStatus;

/**
 * An order in search results. Items are left out on purpose: loading them for every order on the page would
 * cost one extra query per order (N+1). Use GET /api/v1/orders/{id} for the full order.
 */
public record OrderListItemResponse(
        Long id,
        @Schema(nullable = true)
        Long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        @Schema(nullable = true)
        Instant confirmedAt,
        @Schema(nullable = true)
        Instant cancelledAt) {

    public static OrderListItemResponse from(CustomerOrder order) {
        return new OrderListItemResponse(order.getId(), order.getCustomerId(), order.getCustomerName(),
                order.getCustomerEmail(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt(), order.getConfirmedAt(),
                order.getCancelledAt());
    }
}
