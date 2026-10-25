package com.stockflow.order;

import com.stockflow.order.dto.ShippingAddressRequest;

/** The shipping address every test order goes to, unless a test is about the address itself. */
public final class TestAddresses {

    public static final ShippingAddressRequest ADDRESS =
            new ShippingAddressRequest("Ada Lovelace", "Musterstraße", "12a", "10115", "Berlin");

    /** {@link #ADDRESS} as stored on an order, for tests that build the entity directly. */
    public static final ShippingAddress SHIPPING_ADDRESS = ShippingAddress.germanAddress(ADDRESS);

    /** {@link #ADDRESS} as a JSON object, for request bodies. */
    public static final String JSON = """
            {"name": "Ada Lovelace", "street": "Musterstraße", "houseNumber": "12a", "postcode": "10115", "city": "Berlin"}""";

    private TestAddresses() {
    }

    /** Adds {@link #JSON} as "shippingAddress" to an order request body that has only "items". */
    public static String withAddress(String orderJson) {
        String body = orderJson.strip();
        return body.substring(0, body.length() - 1) + ", \"shippingAddress\": " + JSON + "}";
    }
}
