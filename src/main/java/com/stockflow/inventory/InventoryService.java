package com.stockflow.inventory;

import java.time.Clock;
import java.time.Instant;

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
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.dto.StockMovementResponse;
import com.stockflow.product.ProductCreatedEvent;
import com.stockflow.product.ProductNotFoundException;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

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
    public Page<InventoryResponse> listStockLevels(Pageable pageable) {
        return inventoryRepository.findStockLevels(
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), STOCK_LEVEL_ORDER));
    }

    @Transactional
    public InventoryResponse adjust(long productId, StockAdjustmentRequest request) {
        int delta = request.quantityChange();
        if (delta == 0) {
            throw new InvalidRequestParameterException("quantityChange must not be zero");
        }
        Instant now = clock.instant();

        if (inventoryRepository.adjustOnHand(productId, delta, now) == 0) {
            // Nothing updated: either the product does not exist, or the change was not allowed.
            InventoryResponse current = getStockLevel(productId);
            throw new InsufficientStockException("Cannot change on-hand stock of product " + productId + " by "
                    + delta + ": on hand " + current.onHand() + ", reserved " + current.reserved());
        }

        // Our UPDATE holds the row lock until commit, so this read sees exactly the state we produced.
        InventoryResponse updated = getStockLevel(productId);
        stockMovementRepository.save(new StockMovement(productId, MovementType.ADJUSTMENT, delta, 0,
                updated.onHand(), updated.reserved(), request.reason().strip(), now));
        log.info("Adjusted stock productId={} delta={} onHand={}", productId, delta, updated.onHand());
        return updated;
    }

    /** Client sort is ignored: history is always newest first. */
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(long productId, Pageable pageable) {
        if (!inventoryRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return stockMovementRepository.findByProductId(productId,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST))
                .map(StockMovementResponse::from);
    }
}
