package dev.apidocs.core.model;

/** A call to {@code typeName.methodName(...)}; names are simple (unqualified). */
public record MethodRef(String typeName, String methodName) {
}
