package dev.apidocs.core.model;

import java.util.List;

public record ParameterInfo(
        String name,
        ParameterLocation in,
        TypeRef type,
        boolean required,
        String defaultValue,
        List<Constraint> constraints) {

    public ParameterInfo {
        defaultValue = defaultValue == null ? "" : defaultValue;
        constraints = List.copyOf(constraints);
    }
}
