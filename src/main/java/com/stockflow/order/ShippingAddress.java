package com.stockflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.stockflow.order.dto.ShippingAddressRequest;

/**
 * The address an order ships to, copied at checkout and never changed afterwards. Null on orders placed
 * before addresses existed (all six columns NULL; V12 forbids a half-filled address).
 */
@Embeddable
public class ShippingAddress {

    /** ISO 3166-1 alpha-3, the format DHL expects. The only country we ship to for now. */
    public static final String GERMANY = "DEU";

    @Column(name = "ship_name", length = ShippingAddressRequest.NAME_MAX)
    private String name;

    @Column(name = "ship_street", length = ShippingAddressRequest.STREET_MAX)
    private String street;

    @Column(name = "ship_house_number", length = ShippingAddressRequest.HOUSE_NUMBER_MAX)
    private String houseNumber;

    @Column(name = "ship_postcode", length = 5)
    private String postcode;

    @Column(name = "ship_city", length = ShippingAddressRequest.CITY_MAX)
    private String city;

    @Column(name = "ship_country", length = 3)
    private String country;

    protected ShippingAddress() {
        // for JPA
    }

    private ShippingAddress(String name, String street, String houseNumber, String postcode, String city, String country) {
        this.name = name;
        this.street = street;
        this.houseNumber = houseNumber;
        this.postcode = postcode;
        this.city = city;
        this.country = country;
    }

    /** The country is ours to set, never the client's. */
    public static ShippingAddress germanAddress(ShippingAddressRequest request) {
        return new ShippingAddress(request.name(), request.street(), request.houseNumber(), request.postcode(),
                request.city(), GERMANY);
    }

    public String getName() {
        return name;
    }

    public String getStreet() {
        return street;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
