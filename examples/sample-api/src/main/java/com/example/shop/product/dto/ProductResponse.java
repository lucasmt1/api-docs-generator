package com.example.shop.product.dto;

import com.example.shop.product.Product;
import java.math.BigDecimal;

public record ProductResponse(Long id, String sku, String name, BigDecimal price, int stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getPrice(), product.getStock());
    }
}
