-- One stock row per product (single warehouse for now).
-- on_hand: units physically in the warehouse. reserved: units promised to pending orders.
CREATE TABLE inventory (
    product_id BIGINT PRIMARY KEY,
    on_hand    INTEGER     NOT NULL DEFAULT 0,
    reserved   INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved >= 0),
    -- Last line of defence against overselling: you cannot promise more than you hold.
    CONSTRAINT ck_inventory_reserved_within_on_hand CHECK (reserved <= on_hand)
);

-- Products created before this migration get an empty stock row.
INSERT INTO inventory (product_id, on_hand, reserved, updated_at)
SELECT id, 0, 0, now() FROM products;

-- Append-only history of every stock change.
CREATE TABLE stock_movements (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    on_hand_change  INTEGER      NOT NULL,
    reserved_change INTEGER      NOT NULL,
    on_hand_after   INTEGER      NOT NULL,
    reserved_after  INTEGER      NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_movements_type
        CHECK (type IN ('ADJUSTMENT', 'RESERVATION', 'RELEASE', 'FULFILLMENT', 'RESTOCK')),
    CONSTRAINT ck_stock_movements_has_change CHECK (on_hand_change <> 0 OR reserved_change <> 0)
);

-- Serves "history for one product, newest first".
CREATE INDEX ix_stock_movements_product_id_id ON stock_movements (product_id, id DESC);
