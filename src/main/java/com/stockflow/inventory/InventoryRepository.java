package com.stockflow.inventory;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.stockflow.inventory.dto.InventoryResponse;

/**
 * Every stock change is ONE conditional UPDATE. Postgres locks the row for the statement, so the check
 * ({@code WHERE ...}) and the change happen atomically: two concurrent requests can never both pass the
 * check against the same units.
 *
 * <p>Do NOT add {@code clearAutomatically = true}: it would detach entities the caller is still using.
 * It is not needed because Inventory entities are never loaded as managed objects; reads use DTO projections.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    String STOCK_LEVEL_SELECT = """
            SELECT new com.stockflow.inventory.dto.InventoryResponse(
                i.productId, p.sku, p.name, i.onHand, i.reserved, i.onHand - i.reserved, i.updatedAt)
            FROM Inventory i JOIN Product p ON p.id = i.productId
            """;

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO inventory (product_id, on_hand, reserved, updated_at) VALUES (:productId, 0, 0, :now)",
            nativeQuery = true)
    void insertEmpty(long productId, Instant now);

    /**
     * The single statement behind every stock change (adjust, reserve, release, fulfil, restock).
     * The WHERE clause is exactly the table's invariants, evaluated on the values AFTER the change:
     * reserved stays >= 0 and on-hand never drops below reserved. Examples:
     * <ul>
     *   <li>reserve q: (0, +q) passes only if on_hand - reserved >= q, i.e. enough is available</li>
     *   <li>release q: (0, -q) passes only if at least q is reserved</li>
     *   <li>fulfil q: (-q, -q) passes only if at least q is reserved</li>
     * </ul>
     *
     * @return 1 if applied, 0 if the product has no stock row or the change would break an invariant
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.onHand = i.onHand + :onHandChange,
                i.reserved = i.reserved + :reservedChange,
                i.updatedAt = :now
            WHERE i.productId = :productId
              AND i.reserved + :reservedChange >= 0
              AND i.onHand + :onHandChange >= i.reserved + :reservedChange
            """)
    int applyChange(long productId, int onHandChange, int reservedChange, Instant now);

    @Query(STOCK_LEVEL_SELECT + "WHERE i.productId = :productId")
    Optional<InventoryResponse> findStockLevel(long productId);

    /** One query for the whole page (product joined in), so no N+1. */
    @Query(value = STOCK_LEVEL_SELECT, countQuery = "SELECT count(i) FROM Inventory i")
    Page<InventoryResponse> findStockLevels(Pageable pageable);
}
