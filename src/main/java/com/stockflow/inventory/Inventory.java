package com.stockflow.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stock level of one product. Deliberately read-only in Java: rows are inserted and changed only by the
 * atomic statements in {@link InventoryRepository}, never by loading, modifying and saving this entity
 * (that read-check-write would reintroduce the overselling race).
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
        // for JPA
    }

    public Long getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
