package com.stockflow.inventory;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockflow.common.web.PageResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.dto.StockMovementResponse;

@RestController
@RequestMapping(InventoryController.BASE_PATH)
public class InventoryController {

    static final String BASE_PATH = "/api/v1/inventory";

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventoryResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(inventoryService.listStockLevels(pageable)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> get(@PathVariable long productId) {
        return ResponseEntity.ok(inventoryService.getStockLevel(productId));
    }

    @PostMapping("/{productId}/adjustments")
    public ResponseEntity<InventoryResponse> adjust(@PathVariable long productId,
                                                    @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjust(productId, request));
    }

    @GetMapping("/{productId}/movements")
    public ResponseEntity<PageResponse<StockMovementResponse>> movements(@PathVariable long productId,
                                                                         @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(inventoryService.listMovements(productId, pageable)));
    }
}
