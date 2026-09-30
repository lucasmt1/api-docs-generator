package com.example.shop.common.exception;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for " + sku + ": requested " + requested + ", available " + available);
    }
}
