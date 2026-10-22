package com.stockflow.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.common.web.PageResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.InventoryValuationResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.inventory.dto.StockMovementResponse;

@RestController
@Tag(name = "Inventory", description = "Stock levels, manual adjustments and movement history")
@RequestMapping(InventoryController.BASE_PATH)
public class InventoryController {

    static final String BASE_PATH = "/api/v1/inventory";

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "List stock levels of all products, paged")
    @GetMapping
    public ResponseEntity<PageResponse<InventoryResponse>> list(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(inventoryService.listStockLevels(pageable)));
    }

    @Operation(summary = "Report: active products whose available stock is at or below the threshold, lowest first")
    @GetMapping("/low-stock")
    public ResponseEntity<PageResponse<InventoryResponse>> lowStock(
            @RequestParam(defaultValue = "10") @Min(0) @Max(StockAdjustmentRequest.MAX_ADJUSTMENT) int threshold,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(inventoryService.listLowStock(threshold, pageable)));
    }

    @Operation(summary = "Report: total units on hand, reserved and available, and stock value at current prices")
    @GetMapping("/valuation")
    public ResponseEntity<InventoryValuationResponse> valuation() {
        return ResponseEntity.ok(inventoryService.getValuation());
    }

    @Operation(summary = "Get the stock level of one product")
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> get(@PathVariable long productId) {
        return ResponseEntity.ok(inventoryService.getStockLevel(productId));
    }

    @Operation(summary = "Adjust on-hand stock (positive = received, negative = damage/loss). Cannot go below reserved")
    @PostMapping("/{productId}/adjustments")
    public ResponseEntity<InventoryResponse> adjust(@PathVariable long productId,
                                                    @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjust(productId, request));
    }

    @Operation(summary = "Stock movement history of one product, newest first, optionally filtered by type")
    @GetMapping("/{productId}/movements")
    public ResponseEntity<PageResponse<StockMovementResponse>> movements(
            @PathVariable long productId,
            @RequestParam(required = false) MovementType type,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(inventoryService.listMovements(productId, type, pageable)));
    }
}
