package com.stockflow.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param quantityChange signed: positive for goods received, negative for damage, loss or corrections.
 *                       Zero is rejected by the service.
 * @param reason         required, so every change in the history explains itself
 */
public record StockAdjustmentRequest(

        @NotNull
        @Min(-StockAdjustmentRequest.MAX_ADJUSTMENT)
        @Max(StockAdjustmentRequest.MAX_ADJUSTMENT)
        Integer quantityChange,

        @NotBlank
        @Size(max = 255)
        String reason) {

    /** Guards against typos like an extra zero and against integer overflow in the stock columns. */
    public static final int MAX_ADJUSTMENT = 1_000_000;
}
