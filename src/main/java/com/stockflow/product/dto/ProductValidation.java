package com.stockflow.product.dto;

/** Limits shared by the product request DTOs. They mirror the column sizes in V1__create_products.sql. */
final class ProductValidation {

    /** 3-64 chars: letters, digits and hyphens, starting with a letter or digit. Stored upper-case. */
    static final String SKU_PATTERN = "^[A-Za-z0-9][A-Za-z0-9-]{2,63}$";
    static final String SKU_MESSAGE = "must be 3-64 letters, digits or hyphens, starting with a letter or digit";

    static final int NAME_MAX_LENGTH = 200;
    static final int DESCRIPTION_MAX_LENGTH = 2000;

    /** NUMERIC(12, 2): 10 digits before the decimal point, 2 after. */
    static final int PRICE_INTEGER_DIGITS = 10;
    static final int PRICE_FRACTION_DIGITS = 2;

    private ProductValidation() {
    }
}
