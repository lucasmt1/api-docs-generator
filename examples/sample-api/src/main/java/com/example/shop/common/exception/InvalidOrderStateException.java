package com.example.shop.common.exception;

public class InvalidOrderStateException extends BusinessException {

    public InvalidOrderStateException(Long orderId, Object currentStatus, String action) {
        super("Order " + orderId + " cannot " + action + " while " + currentStatus);
    }
}
