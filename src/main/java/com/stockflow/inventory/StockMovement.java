package com.stockflow.inventory;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One immutable line in the stock history. Rows are only ever inserted. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private MovementType type;

    @Column(name = "on_hand_change", nullable = false, updatable = false)
    private int onHandChange;

    @Column(name = "reserved_change", nullable = false, updatable = false)
    private int reservedChange;

    @Column(name = "on_hand_after", nullable = false, updatable = false)
    private int onHandAfter;

    @Column(name = "reserved_after", nullable = false, updatable = false)
    private int reservedAfter;

    @Column(nullable = false, updatable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
        // for JPA
    }

    public StockMovement(long productId, MovementType type, int onHandChange, int reservedChange,
                         int onHandAfter, int reservedAfter, String reason, Instant createdAt) {
        this.productId = productId;
        this.type = type;
        this.onHandChange = onHandChange;
        this.reservedChange = reservedChange;
        this.onHandAfter = onHandAfter;
        this.reservedAfter = reservedAfter;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public MovementType getType() {
        return type;
    }

    public int getOnHandChange() {
        return onHandChange;
    }

    public int getReservedChange() {
        return reservedChange;
    }

    public int getOnHandAfter() {
        return onHandAfter;
    }

    public int getReservedAfter() {
        return reservedAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
