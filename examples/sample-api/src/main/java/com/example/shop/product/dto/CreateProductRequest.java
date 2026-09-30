package com.example.shop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Data required to register a new product in the catalog. */
public record CreateProductRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Z0-9-]+$") String sku,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @PositiveOrZero int stock) {
}
