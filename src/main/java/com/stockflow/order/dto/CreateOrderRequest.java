package com.stockflow.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The customer's name and email are NOT in the request: they come from the logged-in account, so nobody
 * can place an order in someone else's name.
 *
 * @param items the same product may appear more than once; its quantities are merged into one line
 * @param shippingAddress where the parcel goes; stored on the order as it is now
 */
public record CreateOrderRequest(

        @NotEmpty
        @Size(max = CreateOrderRequest.MAX_ITEMS)
        List<@NotNull @Valid OrderItemRequest> items,

        @NotNull
        @Valid
        ShippingAddressRequest shippingAddress) {

    public static final int MAX_ITEMS = 50;
}
