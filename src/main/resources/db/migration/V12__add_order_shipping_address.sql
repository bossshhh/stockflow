-- Shipping address (step 8.7a): a snapshot taken at checkout. It is stored on the order, not looked up from
-- the account, because a parcel goes where it was ordered to, whatever the customer changes later.
-- Lengths are DHL's own field limits, so an address we accept is never rejected by DHL at ship time.
-- All NULL = an order placed before addresses existed; those are shipped manually, without a carrier.
-- Postcode format and country (Germany only for now) are application rules, not constraints here: they
-- change when international shipping arrives, and a CHECK would then need a migration of its own.
ALTER TABLE customer_orders
    ADD COLUMN ship_name         VARCHAR(50),
    ADD COLUMN ship_street       VARCHAR(50),
    ADD COLUMN ship_house_number VARCHAR(10),
    ADD COLUMN ship_postcode     VARCHAR(5),
    ADD COLUMN ship_city         VARCHAR(40),
    ADD COLUMN ship_country      VARCHAR(3),
    -- A half-filled address would print a broken label: all six columns are set, or none.
    ADD CONSTRAINT ck_customer_orders_ship_address CHECK (
        num_nulls(ship_name, ship_street, ship_house_number, ship_postcode, ship_city, ship_country) IN (0, 6));
