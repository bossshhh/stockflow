package com.stockflow.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause of an order search from small, independent conditions. Only the filters the client
 * actually sent are combined, so one method covers every combination without string-concatenated queries.
 */
final class OrderSpecifications {

    private static final String CUSTOMER_ID = "customerId";
    private static final String STATUS = "status";
    private static final String CUSTOMER_EMAIL = "customerEmail";
    private static final String CREATED_AT = "createdAt";

    private OrderSpecifications() {
    }

    static Specification<CustomerOrder> matching(OrderSearchCriteria criteria) {
        List<Specification<CustomerOrder>> conditions = new ArrayList<>();
        if (criteria.customerId() != null) {
            conditions.add(hasCustomerId(criteria.customerId()));
        }
        if (criteria.status() != null) {
            conditions.add(hasStatus(criteria.status()));
        }
        if (criteria.customerEmail() != null && !criteria.customerEmail().isBlank()) {
            conditions.add(hasCustomerEmail(criteria.customerEmail()));
        }
        if (criteria.createdFrom() != null) {
            conditions.add(createdAtOrAfter(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            conditions.add(createdBefore(criteria.createdTo()));
        }
        return Specification.allOf(conditions);
    }

    static Specification<CustomerOrder> hasCustomerId(long customerId) {
        return (root, query, cb) -> cb.equal(root.get(CUSTOMER_ID), customerId);
    }

    static Specification<CustomerOrder> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get(STATUS), status);
    }

    /** Case-insensitive exact match; served by the lower(customer_email) index from V4. */
    static Specification<CustomerOrder> hasCustomerEmail(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get(CUSTOMER_EMAIL)), normalized);
    }

    static Specification<CustomerOrder> createdAtOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    static Specification<CustomerOrder> createdBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get(CREATED_AT), to);
    }
}
