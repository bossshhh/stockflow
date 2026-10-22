package com.stockflow.order;

import java.time.Instant;

/**
 * Optional order search filters; {@code null} means "do not filter on this".
 *
 * @param customerId  owner account; forced to the caller's own id for customers
 * @param createdFrom inclusive lower bound on creation time
 * @param createdTo   exclusive upper bound on creation time
 */
public record OrderSearchCriteria(Long customerId, OrderStatus status, String customerEmail,
                                  Instant createdFrom, Instant createdTo) {

    public OrderSearchCriteria withCustomerId(long id) {
        return new OrderSearchCriteria(id, status, customerEmail, createdFrom, createdTo);
    }
}
