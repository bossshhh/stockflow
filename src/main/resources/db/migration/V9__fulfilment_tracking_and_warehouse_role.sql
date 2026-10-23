-- Fulfilment tracking: after CONFIRMED an order is PACKED, SHIPPED and DELIVERED.
ALTER TABLE customer_orders DROP CONSTRAINT ck_customer_orders_status;
ALTER TABLE customer_orders ADD CONSTRAINT ck_customer_orders_status
    CHECK (status IN ('PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

-- Warehouse workers: fulfil orders (pack, ship, deliver) and read stock, nothing else.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'WAREHOUSE', 'STAFF', 'ADMIN'));

-- Every status change, with who made it and when: drives the tracking timeline and is an audit trail.
-- actor_id has NO foreign key on purpose: an audit log records "user 42 did this" and must not be
-- coupled to (or blocked by) later changes to the users table. NULL = recorded by the system.
CREATE TABLE order_status_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    status      VARCHAR(16) NOT NULL,
    actor_id    BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_order_status_events_order FOREIGN KEY (order_id) REFERENCES customer_orders (id),
    CONSTRAINT ck_order_status_events_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX ix_order_status_events_order_id ON order_status_events (order_id, occurred_at, id);

-- History for orders that existed before this migration, rebuilt from the timestamps they already have.
INSERT INTO order_status_events (order_id, status, actor_id, occurred_at)
SELECT id, 'PENDING', customer_id, created_at FROM customer_orders;
INSERT INTO order_status_events (order_id, status, actor_id, occurred_at)
SELECT id, 'CONFIRMED', NULL, confirmed_at FROM customer_orders WHERE confirmed_at IS NOT NULL;
INSERT INTO order_status_events (order_id, status, actor_id, occurred_at)
SELECT id, 'CANCELLED', NULL, cancelled_at FROM customer_orders WHERE cancelled_at IS NOT NULL;
