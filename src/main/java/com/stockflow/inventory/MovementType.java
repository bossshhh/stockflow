package com.stockflow.inventory;

/** Why stock changed. Must match ck_stock_movements_type in V2__create_inventory.sql. */
public enum MovementType {
    /** Manual correction or goods received: on_hand changes. */
    ADJUSTMENT,
    /** Order created: reserved goes up. */
    RESERVATION,
    /** Pending order cancelled: reserved goes down. */
    RELEASE,
    /** Order confirmed: on_hand and reserved both go down. */
    FULFILLMENT,
    /** Confirmed order cancelled: on_hand goes back up. */
    RESTOCK
}
