-- Order search: "status X, newest first" and "status X between two dates".
CREATE INDEX ix_customer_orders_status_created_at ON customer_orders (status, created_at);

-- Order search without a status filter, and the order summary report (date range only).
CREATE INDEX ix_customer_orders_created_at ON customer_orders (created_at);

-- Case-insensitive email search uses lower(customer_email); a plain index on the column would not be used.
CREATE INDEX ix_customer_orders_lower_customer_email ON customer_orders (lower(customer_email));
