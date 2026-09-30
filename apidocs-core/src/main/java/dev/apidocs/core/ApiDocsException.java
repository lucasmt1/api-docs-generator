package dev.apidocs.core;

/** Base class for expected, user-facing failures. */
public class ApiDocsException extends RuntimeException {

    public ApiDocsException(String message) {
        super(message);
    }

    public ApiDocsException(String message, Throwable cause) {
        super(message, cause);
    }
}
