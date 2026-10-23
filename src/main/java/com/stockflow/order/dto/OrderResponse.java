package com.stockflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stockflow.order.CustomerOrder;
import com.stockflow.order.OrderStatus;

public record OrderResponse(
        Long id,
        @Schema(nullable = true)
        Long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        List<OrderStatusEventResponse> history,
        Instant createdAt,
        Instant updatedAt,
        @Schema(nullable = true)
        Instant confirmedAt,
        @Schema(nullable = true)
        Instant cancelledAt) {

    /** Must be called inside a transaction: it reads the (lazy) items and history. */
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getCustomerName(),
                order.getCustomerEmail(), order.getStatus(), order.getTotalAmount(), order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getHistory().stream().map(OrderStatusEventResponse::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getConfirmedAt(), order.getCancelledAt());
    }
}
