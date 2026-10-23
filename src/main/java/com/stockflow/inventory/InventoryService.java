package com.stockflow.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.InventoryValuationResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.dto.StockMovementResponse;
import com.stockflow.product.ProductCreatedEvent;
import com.stockflow.product.ProductNotFoundException;

/**
 * Owns every stock change. Each one is a single atomic conditional UPDATE plus a history row, in the
 * caller's transaction. The order-facing methods (reserve, release, fulfil, restock) are MANDATORY: they
 * must run inside the order's transaction so that order and stock always commit or roll back together.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /**
     * Ceiling for on-hand stock of one product. Far above any real warehouse, and far enough below the
     * INTEGER maximum (2,147,483,647) that on_hand plus any allowed change can never overflow.
     */
    static final long MAX_ON_HAND = 1_000_000_000L;

    private static final Sort STOCK_LEVEL_ORDER = Sort.by("productId");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    public InventoryService(InventoryRepository inventoryRepository,
                            StockMovementRepository stockMovementRepository,
                            Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.clock = clock;
    }

    /**
     * Gives every new product an empty stock row. MANDATORY: this must run inside the product's creation
     * transaction, so a product can never exist without its stock row.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onProductCreated(ProductCreatedEvent event) {
        inventoryRepository.insertEmpty(event.productId(), clock.instant());
    }

    @Transactional(readOnly = true)
    public InventoryResponse getStockLevel(long productId) {
        return inventoryRepository.findStockLevel(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /** Client sort is ignored: stock levels are always listed by product id. */
    @Transactional(readOnly = true)
    /**
     * @param query optional case-insensitive substring of the product name or SKU; blank means no filter
     */
    public Page<InventoryResponse> listStockLevels(String query, Pageable pageable) {
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), STOCK_LEVEL_ORDER);
        if (query == null || query.isBlank()) {
            return inventoryRepository.findStockLevels(ordered);
        }
        return inventoryRepository.findStockLevelsMatching(CatalogService.containsPattern(query), ordered);
    }

    /** Manual change to on-hand stock (goods received, damage, stock count corrections). */
    @Transactional
    public InventoryResponse adjust(long productId, StockAdjustmentRequest request) {
        int delta = request.quantityChange();
        if (delta == 0) {
            throw new InvalidRequestParameterException("quantityChange must not be zero");
        }
        InventoryResponse updated = applyChange(productId, MovementType.ADJUSTMENT, delta, 0, null,
                request.reason().strip(),
                current -> delta > 0
                        ? new StockLimitExceededException(productId, current.onHand(), MAX_ON_HAND)
                        : new InsufficientStockException("Cannot change on-hand stock of product " + productId
                                + " by " + delta + ": on hand " + current.onHand() + ", reserved "
                                + current.reserved()));
        log.info("Adjusted stock productId={} delta={} onHand={}", productId, delta, updated.onHand());
        return updated;
    }

    /** Order placed: sets {@code quantity} aside. Fails if not enough is available. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long productId, int quantity, long orderId) {
        requirePositive(quantity);
        // Customer-facing message: names the SKU but does not reveal stock counts.
        applyChange(productId, MovementType.RESERVATION, 0, quantity, orderId, "Reserved for order " + orderId,
                current -> new InsufficientStockException("Not enough stock available for SKU " + current.sku()));
    }

    /** Pending order cancelled: gives the reservation back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long productId, int quantity, long orderId) {
        requirePositive(quantity);
        applyChange(productId, MovementType.RELEASE, 0, -quantity, orderId, "Released by cancelled order " + orderId,
                current -> inconsistent(current, orderId));
    }

    /** Order confirmed: the reserved units leave the warehouse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fulfill(long productId, int quantity, long orderId) {
        requirePositive(quantity);
        applyChange(productId, MovementType.FULFILLMENT, -quantity, -quantity, orderId,
                "Fulfilled order " + orderId, current -> inconsistent(current, orderId));
    }

    /** Confirmed order cancelled in time: the units come back on hand. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(long productId, int quantity, long orderId) {
        requirePositive(quantity);
        applyChange(productId, MovementType.RESTOCK, quantity, 0, orderId, "Restocked from cancelled order " + orderId,
                current -> inconsistent(current, orderId));
    }

    /**
     * Client sort is ignored: history is always newest first.
     *
     * @param type {@code null} returns movements of every type
     */
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(long productId, MovementType type, Pageable pageable) {
        if (!inventoryRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        PageRequest page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Page<StockMovement> movements = type == null
                ? stockMovementRepository.findByProductId(productId, page)
                : stockMovementRepository.findByProductIdAndType(productId, type, page);
        return movements.map(StockMovementResponse::from);
    }

    /** Report: active products with available stock at or below the threshold, lowest first. */
    @Transactional(readOnly = true)
    public Page<InventoryResponse> listLowStock(int threshold, Pageable pageable) {
        return inventoryRepository.findLowStock(threshold,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }

    /** Report: stock totals and value at current prices, across all products. */
    @Transactional(readOnly = true)
    public InventoryValuationResponse getValuation() {
        return inventoryRepository.findValuation();
    }

    /**
     * Runs the atomic UPDATE and records the movement. If the UPDATE changes nothing, the product either does
     * not exist (404) or the change would break an invariant, and {@code rejection} builds the error.
     */
    private InventoryResponse applyChange(long productId, MovementType type, int onHandChange, int reservedChange,
                                          Long orderId, String reason,
                                          Function<InventoryResponse, RuntimeException> rejection) {
        Instant now = clock.instant();
        if (inventoryRepository.applyChange(productId, onHandChange, reservedChange, MAX_ON_HAND, now) == 0) {
            throw rejection.apply(getStockLevel(productId));
        }
        // Our UPDATE holds the row lock until commit, so this read sees exactly the state we produced.
        InventoryResponse updated = getStockLevel(productId);
        stockMovementRepository.save(new StockMovement(productId, type, onHandChange, reservedChange,
                updated.onHand(), updated.reserved(), orderId, reason, now));
        return updated;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }

    /** Release/fulfil/restock should never be refused; if one is, stock and orders disagree. Fail loudly (500). */
    private static IllegalStateException inconsistent(InventoryResponse current, long orderId) {
        return new IllegalStateException("Stock of product " + current.productId() + " is inconsistent with order "
                + orderId + ": on hand " + current.onHand() + ", reserved " + current.reserved());
    }
}
