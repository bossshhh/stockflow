package com.stockflow.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A sellable item. There are no setters: state changes go through methods that keep the entity valid.
 * The SKU never changes after creation, and products are deactivated rather than deleted so order
 * history keeps pointing at them.
 */
@Entity
@Table(name = "products")
public class Product {

    /** Must match the constraint name in V1__create_products.sql. */
    public static final String SKU_UNIQUE_CONSTRAINT = "uk_products_sku";

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // for JPA
    }

    public Product(String sku, String name, String description, BigDecimal price, Instant now) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = toMoneyScale(price);
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String name, String description, BigDecimal price, Instant now) {
        this.name = name;
        this.description = description;
        this.price = toMoneyScale(price);
        this.updatedAt = now;
    }

    /** Idempotent: deactivating an inactive product changes nothing. */
    public void deactivate(Instant now) {
        if (!active) {
            return;
        }
        this.active = false;
        this.updatedAt = now;
    }

    // Callers validate at most 2 decimals, so UNNECESSARY never rounds; it throws if that contract breaks.
    private static BigDecimal toMoneyScale(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
