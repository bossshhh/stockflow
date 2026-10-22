package com.stockflow.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

        @NotNull
        @Positive
        Long productId,

        @NotNull
        @Min(1)
        @Max(OrderItemRequest.MAX_QUANTITY)
        Integer quantity) {

    public static final int MAX_QUANTITY = 10_000;
}
