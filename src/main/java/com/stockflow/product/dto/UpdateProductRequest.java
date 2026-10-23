package com.stockflow.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Full replacement of the editable fields. The SKU is deliberately absent: it never changes. */
public record UpdateProductRequest(

        @NotBlank
        @Size(max = ProductValidation.NAME_MAX_LENGTH)
        String name,

        @Size(max = ProductValidation.DESCRIPTION_MAX_LENGTH)
        @Schema(nullable = true)
        String description,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = ProductValidation.PRICE_INTEGER_DIGITS, fraction = ProductValidation.PRICE_FRACTION_DIGITS)
        BigDecimal price) {
}
