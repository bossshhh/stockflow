package com.stockflow.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A customer order and its lifecycle rules. The entity decides WHETHER a transition is allowed;
 * {@link OrderService} performs the matching stock changes. Allowed transitions:
 * <pre>
 *   PENDING   --confirm--> CONFIRMED
 *   PENDING   --cancel---> CANCELLED   (reservation released)
 *   CONFIRMED --cancel---> CANCELLED   (only while now <= confirmedAt + window; stock restocked)
 * </pre>
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owner account. Null only for orders placed before accounts existed (V5). */
    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
        // for JPA
    }

    /** customerName and customerEmail are snapshots of the account at ordering time. */
    public CustomerOrder(long customerId, String customerName, String customerEmail, Instant now) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(long productId, String sku, String productName, BigDecimal unitPrice, int quantity) {
        requireStatus(OrderStatus.PENDING, "add items to");
        OrderItem item = new OrderItem(this, productId, sku, productName, unitPrice, quantity);
        items.add(item);
        totalAmount = totalAmount.add(item.getLineTotal());
    }

    public void confirm(Instant now) {
        requireStatus(OrderStatus.PENDING, "confirm");
        status = OrderStatus.CONFIRMED;
        confirmedAt = now;
        updatedAt = now;
    }

    /**
     * @param confirmedCancelWindow a confirmed order may be cancelled while {@code now <= confirmedAt + window}
     * @throws InvalidOrderStateException   if the order is already cancelled
     * @throws CancelWindowExpiredException if a confirmed order is past its window
     */
    public void cancel(Instant now, Duration confirmedCancelWindow) {
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(id, status, "cancel");
        }
        if (status == OrderStatus.CONFIRMED && now.isAfter(confirmedAt.plus(confirmedCancelWindow))) {
            throw new CancelWindowExpiredException(id, confirmedCancelWindow);
        }
        status = OrderStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
    }

    private void requireStatus(OrderStatus required, String action) {
        if (status != required) {
            throw new InvalidOrderStateException(id, status, action);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public boolean isOwnedBy(long userId) {
        return customerId != null && customerId == userId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
