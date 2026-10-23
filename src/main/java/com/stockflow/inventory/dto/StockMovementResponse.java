package com.stockflow.inventory.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(nullable = true)
        Long orderId,
        String reason,
        Instant createdAt) {

    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getProductId(), movement.getType(),
                movement.getOnHandChange(), movement.getReservedChange(), movement.getOnHandAfter(),
                movement.getReservedAfter(), movement.getOrderId(), movement.getReason(), movement.getCreatedAt());
    }
}
