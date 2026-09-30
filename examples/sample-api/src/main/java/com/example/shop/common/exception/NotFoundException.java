package com.example.shop.common.exception;

/** Raised when a requested resource does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
