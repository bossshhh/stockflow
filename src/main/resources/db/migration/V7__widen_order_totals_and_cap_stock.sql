-- QA finding: the largest order the validation rules allow (50 lines x 10,000 units x 9,999,999,999.99)
-- is about 5e15, but NUMERIC(14,2) holds at most 999,999,999,999.99 (12 integer digits), so such orders
-- failed with "numeric field overflow". NUMERIC(18,2) (16 integer digits) fits every allowed order.
ALTER TABLE order_items ALTER COLUMN line_total TYPE NUMERIC(18, 2);
ALTER TABLE customer_orders ALTER COLUMN total_amount TYPE NUMERIC(18, 2);

-- QA finding: repeated adjustments could push on_hand past the INTEGER maximum (2,147,483,647) and every
-- later stock change on that product failed. The service now caps stock at 1,000,000,000; this is the
-- database-level backstop. NOT VALID: enforced for every new write, but existing rows are not checked,
-- so the migration cannot fail on a database that already holds an over-limit row.
ALTER TABLE inventory
    ADD CONSTRAINT ck_inventory_on_hand_ceiling CHECK (on_hand <= 1000000000) NOT VALID;
