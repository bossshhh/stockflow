package com.stockflow.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateProductRequest(

        @NotBlank
        @Pattern(regexp = ProductValidation.SKU_PATTERN, message = ProductValidation.SKU_MESSAGE)
        String sku,

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
