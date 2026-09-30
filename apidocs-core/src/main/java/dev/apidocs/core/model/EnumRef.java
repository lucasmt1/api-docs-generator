package dev.apidocs.core.model;

/** Reference to an enum schema in {@link ApiModel#schemas()}. */
public record EnumRef(String schemaName) implements TypeRef {
}
