package com.example.shop.common;

import java.time.Instant;

/** Error body returned by the global exception handler. */
public record ApiError(int status, String error, String message, Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
