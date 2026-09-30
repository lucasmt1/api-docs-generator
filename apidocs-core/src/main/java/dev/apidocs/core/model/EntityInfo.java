package dev.apidocs.core.model;

import java.util.List;

public record EntityInfo(
        String name,
        String qualifiedName,
        String table,
        List<EntityField> fields,
        List<Relationship> relationships) {

    public EntityInfo {
        fields = List.copyOf(fields);
        relationships = List.copyOf(relationships);
    }
}
