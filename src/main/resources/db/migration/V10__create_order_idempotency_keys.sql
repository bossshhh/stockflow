-- Idempotency keys for placing orders (request header Idempotency-Key). A retried request with the same
-- key returns the order that key already created, instead of creating and reserving a second one.
-- Keys belong to one customer (primary key customer_id + key), so customers cannot collide or probe.
-- request_hash: SHA-256 of the merged items, to refuse the same key with a different order.
-- order_id is NULL only inside the transaction that claims the key; it is set before that one commits,
-- and a failed attempt rolls the claim back with everything else.
CREATE TABLE order_idempotency_keys (
    customer_id     BIGINT      NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    order_id        BIGINT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_idempotency_keys PRIMARY KEY (customer_id, idempotency_key),
    CONSTRAINT fk_order_idempotency_keys_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_order_idempotency_keys_order FOREIGN KEY (order_id) REFERENCES customer_orders (id)
);
