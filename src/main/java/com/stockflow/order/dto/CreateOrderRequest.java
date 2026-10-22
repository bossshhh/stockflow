package com.stockflow.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param items the same product may appear more than once; its quantities are merged into one line
 */
public record CreateOrderRequest(

        @NotBlank
        @Size(max = 200)
        String customerName,

        @NotBlank
        @Email
        @Size(max = 254)
        String customerEmail,

        @NotEmpty
        @Size(max = CreateOrderRequest.MAX_ITEMS)
        List<@NotNull @Valid OrderItemRequest> items) {

    public static final int MAX_ITEMS = 50;
}
