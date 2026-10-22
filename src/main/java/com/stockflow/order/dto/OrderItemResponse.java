package com.stockflow.order.dto;

import java.math.BigDecimal;

import com.stockflow.order.OrderItem;

public record OrderItemResponse(
        Long productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getSku(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }
}
