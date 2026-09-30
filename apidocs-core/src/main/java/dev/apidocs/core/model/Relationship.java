package dev.apidocs.core.model;

/** {@code mappedBy} is empty for the owning side. */
public record Relationship(String field, RelationKind kind, String target, String mappedBy) {

    public Relationship {
        mappedBy = mappedBy == null ? "" : mappedBy;
    }
}
