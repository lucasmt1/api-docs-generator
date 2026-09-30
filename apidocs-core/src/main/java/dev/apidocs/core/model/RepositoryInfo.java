package dev.apidocs.core.model;

/** {@code entity} and {@code idType} are empty when they cannot be inferred. */
public record RepositoryInfo(String name, String qualifiedName, String entity, String idType) {
}
