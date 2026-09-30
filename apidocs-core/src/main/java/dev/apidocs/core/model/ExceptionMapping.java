package dev.apidocs.core.model;

/** An exception (simple name) and the HTTP status Spring answers with. */
public record ExceptionMapping(String exception, int status) {
}
