package com.example.shop.common.exception;

/** Base class for violations of business rules. */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
