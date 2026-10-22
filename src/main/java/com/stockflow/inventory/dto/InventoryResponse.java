package com.stockflow.inventory.dto;

import java.time.Instant;

/**
 * Stock level of one product. Built directly by the JPQL query in InventoryRepository.
 *
 * @param available units that can still be ordered: onHand - reserved
 */
public record InventoryResponse(
        Long productId,
        String sku,
        String name,
        int onHand,
        int reserved,
        int available,
        Instant updatedAt) {
}
