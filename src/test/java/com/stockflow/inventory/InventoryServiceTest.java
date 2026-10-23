package com.stockflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.ProductNotFoundException;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final long PRODUCT_ID = 7L;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, stockMovementRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adjustRecordsMovementWithResultingLevels() {
        when(inventoryRepository.applyChange(PRODUCT_ID, 10, 0, InventoryService.MAX_ON_HAND, NOW)).thenReturn(1);
        when(inventoryRepository.findStockLevel(PRODUCT_ID)).thenReturn(Optional.of(level(10, 3)));

        InventoryResponse result = inventoryService.adjust(PRODUCT_ID, new StockAdjustmentRequest(10, " delivery "));

        assertThat(result.onHand()).isEqualTo(10);
        ArgumentCaptor<StockMovement> saved = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(saved.capture());
        StockMovement movement = saved.getValue();
        assertThat(movement.getType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movement.getOnHandChange()).isEqualTo(10);
        assertThat(movement.getReservedChange()).isZero();
        assertThat(movement.getOnHandAfter()).isEqualTo(10);
        assertThat(movement.getReservedAfter()).isEqualTo(3);
        assertThat(movement.getReason()).isEqualTo("delivery");
        assertThat(movement.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void adjustRejectsZeroWithoutTouchingStock() {
        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, new StockAdjustmentRequest(0, "nothing")))
                .isInstanceOf(InvalidRequestParameterException.class);
        verify(inventoryRepository, never()).applyChange(anyLong(), anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    void adjustRejectedByConditionThrowsInsufficientStockAndRecordsNothing() {
        when(inventoryRepository.applyChange(PRODUCT_ID, -5, 0, InventoryService.MAX_ON_HAND, NOW)).thenReturn(0);
        when(inventoryRepository.findStockLevel(PRODUCT_ID)).thenReturn(Optional.of(level(4, 2)));

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, new StockAdjustmentRequest(-5, "damaged")))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("on hand 4")
                .hasMessageContaining("reserved 2");
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void adjustOnUnknownProductThrowsNotFound() {
        when(inventoryRepository.applyChange(PRODUCT_ID, 1, 0, InventoryService.MAX_ON_HAND, NOW)).thenReturn(0);
        when(inventoryRepository.findStockLevel(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjust(PRODUCT_ID, new StockAdjustmentRequest(1, "x")))
                .isInstanceOf(ProductNotFoundException.class);
        verify(stockMovementRepository, never()).save(any());
    }

    private static InventoryResponse level(int onHand, int reserved) {
        return new InventoryResponse(PRODUCT_ID, "SKU-7", "Product 7", onHand, reserved, onHand - reserved, NOW);
    }
}
