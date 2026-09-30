package com.example.shop.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Relative stock change; negative values remove units. */
public record StockAdjustmentRequest(@NotNull Integer delta, @Size(max = 200) String reason) {
}
