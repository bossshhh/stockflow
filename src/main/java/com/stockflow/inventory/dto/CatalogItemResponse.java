package com.stockflow.inventory.dto;

import java.math.BigDecimal;

/**
 * A product as shoppers see it. Deliberately exposes only whether something can be ordered, never the
 * stock counts (competitors and customers do not need to know how much is on the shelf).
 * Built directly by the JPQL query in InventoryRepository.
 */
public record CatalogItemResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        boolean inStock) {
}
