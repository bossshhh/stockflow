package com.stockflow.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One immutable entry in an order's status history. Created only by {@link CustomerOrder} in the same
 * method that changes the status, so the history can never disagree with the current status.
 */
@Entity
@Table(name = "order_status_events")
public class OrderStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private OrderStatus status;

    /** Who caused the change (a user id); null when recorded by the system. */
    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected OrderStatusEvent() {
        // for JPA
    }

    OrderStatusEvent(CustomerOrder order, OrderStatus status, Long actorId, Instant occurredAt) {
        this.order = order;
        this.status = status;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
