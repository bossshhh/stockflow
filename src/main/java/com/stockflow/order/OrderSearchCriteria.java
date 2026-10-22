package com.stockflow.order;

import java.time.Instant;

/**
 * Optional order search filters; {@code null} means "do not filter on this".
 *
 * @param createdFrom inclusive lower bound on creation time
 * @param createdTo   exclusive upper bound on creation time
 */
public record OrderSearchCriteria(OrderStatus status, String customerEmail, Instant createdFrom, Instant createdTo) {
}
