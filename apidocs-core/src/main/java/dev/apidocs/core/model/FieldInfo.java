package dev.apidocs.core.model;

import java.util.List;

public record FieldInfo(String name, TypeRef type, boolean required, List<Constraint> constraints, String description) {

    public FieldInfo {
        constraints = List.copyOf(constraints);
        description = description == null ? "" : description;
    }
}
