package dev.apidocs.core.model;

/** A type the tool cannot describe (external library type, {@code Object}); rendered as a generic object. */
public record OpaqueType(String javaType) implements TypeRef {
}
