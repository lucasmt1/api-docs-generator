package dev.apidocs.core.model;

/** A persisted column. {@code length} is 0 when not declared. */
public record EntityField(
        String name,
        TypeRef type,
        String column,
        boolean id,
        boolean nullable,
        boolean unique,
        int length) {
}
