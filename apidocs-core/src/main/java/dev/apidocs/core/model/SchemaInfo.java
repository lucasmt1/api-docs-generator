package dev.apidocs.core.model;

import java.util.List;

/** A DTO (or exposed entity/enum) as seen on the wire. {@code entity} is true for JPA/Mongo entities. */
public record SchemaInfo(
        String name,
        String qualifiedName,
        SchemaKind kind,
        List<FieldInfo> fields,
        List<String> enumValues,
        String description,
        boolean entity) {

    public SchemaInfo {
        fields = List.copyOf(fields);
        enumValues = List.copyOf(enumValues);
        description = description == null ? "" : description;
    }
}
