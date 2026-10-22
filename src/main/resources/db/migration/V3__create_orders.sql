-- "customer_orders" because ORDER is a reserved word in SQL.
CREATE TABLE customer_orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_name  VARCHAR(200)   NOT NULL,
    customer_email VARCHAR(254)   NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    total_amount   NUMERIC(14, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    confirmed_at   TIMESTAMPTZ,
    cancelled_at   TIMESTAMPTZ,
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_customer_orders_total_non_negative CHECK (total_amount >= 0)
);

-- sku, product_name and unit_price are snapshots taken when the order is placed:
-- later product edits must not rewrite what the customer agreed to pay.
CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    sku          VARCHAR(64)    NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_total   NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    -- Duplicate products in a request are merged into one line.
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_non_negative CHECK (unit_price >= 0)
);

-- Links stock changes caused by an order back to it (NULL for manual adjustments).
ALTER TABLE stock_movements
    ADD COLUMN order_id BIGINT,
    ADD CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES customer_orders (id);
