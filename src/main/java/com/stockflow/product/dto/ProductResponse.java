package com.stockflow.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import com.stockflow.product.Product;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        @Schema(nullable = true)
        String description,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt());
    }
}
