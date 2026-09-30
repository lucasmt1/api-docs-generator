package com.example.shop.order.dto;

import com.example.shop.order.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(Long productId, String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProduct().getId(), item.getProduct().getSku(), item.getQuantity(),
                item.getUnitPrice(), item.getLineTotal());
    }
}
