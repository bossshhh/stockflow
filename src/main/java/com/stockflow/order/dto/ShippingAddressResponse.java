package com.stockflow.order.dto;

import com.stockflow.order.ShippingAddress;

public record ShippingAddressResponse(String name, String street, String houseNumber, String postcode, String city,
                                      String country) {

    /** Null for orders placed before addresses existed. */
    public static ShippingAddressResponse from(ShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new ShippingAddressResponse(address.getName(), address.getStreet(), address.getHouseNumber(),
                address.getPostcode(), address.getCity(), address.getCountry());
    }
}
