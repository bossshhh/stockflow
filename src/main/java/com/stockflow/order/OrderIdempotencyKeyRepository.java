package com.stockflow.order;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL instead of JPA: the claim is an upsert with a conditional {@code DO UPDATE}, which JPA cannot
 * express, and nothing here needs an entity. Runs in the caller's transaction (same connection).
 */
@Repository
class OrderIdempotencyKeyRepository {

    record StoredKey(String requestHash, Long orderId) {
    }

    private final JdbcClient jdbc;

    OrderIdempotencyKeyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims the key for the current transaction. Returns false if a live claim already exists.
     *
     * <p>If another transaction holds an uncommitted claim on the same key, PostgreSQL makes this INSERT
     * wait for it (primary key), then acts on its outcome: committed means "already used" (false), rolled
     * back means the key is free and this call claims it. A claim created before {@code expiredBefore} is
     * expired and is taken over in the same statement.
     */
    boolean claim(long customerId, String key, String requestHash, Instant now, Instant expiredBefore) {
        return jdbc.sql("""
                        INSERT INTO order_idempotency_keys (customer_id, idempotency_key, request_hash, created_at)
                        VALUES (:customerId, :key, :requestHash, :now)
                        ON CONFLICT (customer_id, idempotency_key) DO UPDATE
                            SET request_hash = EXCLUDED.request_hash, order_id = NULL, created_at = EXCLUDED.created_at
                            WHERE order_idempotency_keys.created_at < :expiredBefore
                        """)
                .param("customerId", customerId)
                .param("key", key)
                .param("requestHash", requestHash)
                .param("now", utc(now))
                .param("expiredBefore", utc(expiredBefore))
                .update() == 1;
    }

    void attachOrder(long customerId, String key, long orderId) {
        jdbc.sql("""
                        UPDATE order_idempotency_keys SET order_id = :orderId
                        WHERE customer_id = :customerId AND idempotency_key = :key
                        """)
                .param("orderId", orderId)
                .param("customerId", customerId)
                .param("key", key)
                .update();
    }

    Optional<StoredKey> find(long customerId, String key) {
        return jdbc.sql("""
                        SELECT request_hash, order_id FROM order_idempotency_keys
                        WHERE customer_id = :customerId AND idempotency_key = :key
                        """)
                .param("customerId", customerId)
                .param("key", key)
                .query((rs, rowNum) -> new StoredKey(rs.getString("request_hash"), rs.getObject("order_id", Long.class)))
                .optional();
    }

    /** The PostgreSQL driver binds OffsetDateTime to TIMESTAMPTZ; it does not accept Instant. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
