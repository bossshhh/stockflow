package com.stockflow.inventory.dto;

import java.time.Instant;

import com.stockflow.inventory.MovementType;
import com.stockflow.inventory.StockMovement;

public record StockMovementResponse(
        Long id,
        Long productId,
        MovementType type,
        int onHandChange,
        int reservedChange,
        int onHandAfter,
        int reservedAfter,
        String reason,
        Instant createdAt) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getProductId(), movement.getType(),
                movement.getOnHandChange(), movement.getReservedChange(), movement.getOnHandAfter(),
                movement.getReservedAfter(), movement.getReason(), movement.getCreatedAt());
    }
}
