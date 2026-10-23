-- Correction to V7. A CHECK constraint, even one added NOT VALID, is enforced on EVERY later UPDATE of a
-- row. A row that was already above the ceiling therefore could not be reduced step by step (each step
-- still leaves it above the ceiling), so it could never be corrected. The ceiling only has to stop stock
-- from GROWING past the limit, which the conditional UPDATE in InventoryRepository.applyChange enforces;
-- overflow is impossible there because the ceiling arithmetic runs in bigint.
ALTER TABLE inventory DROP CONSTRAINT ck_inventory_on_hand_ceiling;
