package com.stockflow.order;

/** PENDING -> CONFIRMED -> CANCELLED, or PENDING -> CANCELLED. CANCELLED is terminal. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
