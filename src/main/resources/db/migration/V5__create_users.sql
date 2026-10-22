CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Stored lower-case, so the unique constraint is case-insensitive in practice.
    email         VARCHAR(254) NOT NULL,
    -- BCrypt hash (60 chars). The plain password is never stored.
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN'))
);

-- Orders now belong to a customer account. Nullable: orders placed before accounts existed have no owner.
ALTER TABLE customer_orders
    ADD COLUMN customer_id BIGINT,
    ADD CONSTRAINT fk_customer_orders_customer FOREIGN KEY (customer_id) REFERENCES users (id);

-- "My orders, newest first".
CREATE INDEX ix_customer_orders_customer_id_created_at ON customer_orders (customer_id, created_at);
