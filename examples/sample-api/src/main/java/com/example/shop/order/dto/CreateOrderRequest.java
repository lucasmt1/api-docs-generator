package com.example.shop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Items to buy for an existing customer. */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotEmpty @Size(max = 50) @Valid List<OrderItemRequest> items) {
}
