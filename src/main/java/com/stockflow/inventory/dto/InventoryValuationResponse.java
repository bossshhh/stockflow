package com.stockflow.inventory.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stock totals across all products.
 *
 * @param totalValue on-hand units valued at each product's CURRENT price
 */
public record InventoryValuationResponse(
        long productCount,
        long totalOnHand,
        long totalReserved,
        long totalAvailable,
        BigDecimal totalValue) {

    /**
     * Used by the JPQL aggregate query. SUM over zero rows is NULL in SQL, so nulls become zero here.
     */
    public InventoryValuationResponse(Long productCount, Long totalOnHand, Long totalReserved, BigDecimal totalValue) {
        this(zeroIfNull(productCount), zeroIfNull(totalOnHand), zeroIfNull(totalReserved),
                zeroIfNull(totalOnHand) - zeroIfNull(totalReserved),
                (totalValue == null ? BigDecimal.ZERO : totalValue).setScale(2, RoundingMode.UNNECESSARY));
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
