package com.stockflow.order;

/**
 * PENDING -> CONFIRMED -> PACKED -> SHIPPED -> DELIVERED, or cancelled before packing.
 * CANCELLED and DELIVERED are terminal. Must match ck_customer_orders_status (V9).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    /** A sale that counts as revenue: confirmed (stock deducted) and not cancelled. */
    public boolean isFulfilmentStage() {
        return this == CONFIRMED || this == PACKED || this == SHIPPED || this == DELIVERED;
    }
}
