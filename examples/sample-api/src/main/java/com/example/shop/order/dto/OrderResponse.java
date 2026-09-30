package com.example.shop.order.dto;

import com.example.shop.order.Order;
import com.example.shop.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getSubtotal(), order.getDiscount(), order.getTotal(), order.getCreatedAt());
    }
}
