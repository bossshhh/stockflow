package com.stockflow.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    /** Order and its items in one query (no extra query for the items). */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(long id);

    /**
     * SELECT ... FOR UPDATE: locks the order row until the transaction ends, so two concurrent
     * confirm/cancel calls on the same order run one after the other and the second sees the new status.
     * Items are not fetch-joined here: Postgres cannot lock the nullable side of an outer join.
     */
    /** Oldest first, served by ix_customer_orders_status_created_at. */
    @Query("SELECT o.id FROM CustomerOrder o WHERE o.status = :status AND o.createdAt < :createdBefore ORDER BY o.createdAt, o.id")
    List<Long> findIdsByStatusCreatedBefore(OrderStatus status, Instant createdBefore, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(long id);

    /** Count and total per status for orders created in [from, to), aggregated by the database. */
    @Query("""
            SELECT new com.stockflow.order.OrderStatusTotal(o.status, count(o), sum(o.totalAmount))
            FROM CustomerOrder o
            WHERE o.createdAt >= :from AND o.createdAt < :to
            GROUP BY o.status
            """)
    List<OrderStatusTotal> totalsByStatus(Instant from, Instant to);
}
