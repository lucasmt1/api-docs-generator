package dev.apidocs.core.model;

/** Reference to a schema in {@link ApiModel#schemas()}. */
public record ObjectRef(String schemaName) implements TypeRef {
}
