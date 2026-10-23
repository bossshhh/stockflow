package com.stockflow.order.dto;

import java.time.Instant;

import com.stockflow.order.OrderStatus;
import com.stockflow.order.OrderStatusEvent;

/**
 * One checkpoint in an order's history. Who made the change is stored for auditing but not exposed:
 * customers have no business seeing staff identities.
 */
public record OrderStatusEventResponse(OrderStatus status, Instant at) {

    public static OrderStatusEventResponse from(OrderStatusEvent event) {
        return new OrderStatusEventResponse(event.getStatus(), event.getOccurredAt());
    }
}
