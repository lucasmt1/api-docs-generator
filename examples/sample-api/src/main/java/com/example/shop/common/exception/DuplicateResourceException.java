package com.example.shop.common.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resource, String field, String value) {
        super(resource + " with " + field + " '" + value + "' already exists");
    }
}
