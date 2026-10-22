package com.stockflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stockflow.order.CustomerOrder;
import com.stockflow.order.OrderStatus;

public record OrderResponse(
        Long id,
        String customerName,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant cancelledAt) {

    /** Must be called inside a transaction: it reads the (lazy) items. */
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(order.getId(), order.getCustomerName(), order.getCustomerEmail(), order.getStatus(),
                order.getTotalAmount(), order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getConfirmedAt(), order.getCancelledAt());
    }
}
