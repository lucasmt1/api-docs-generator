package dev.apidocs.core.model;

import java.util.List;

public record ControllerInfo(
        String name,
        String qualifiedName,
        String basePath,
        String description,
        List<String> dependencies,
        List<EndpointInfo> endpoints) {

    public ControllerInfo {
        dependencies = List.copyOf(dependencies);
        endpoints = List.copyOf(endpoints);
    }

    public ControllerInfo withEndpoints(List<EndpointInfo> newEndpoints) {
        return new ControllerInfo(name, qualifiedName, basePath, description, dependencies, newEndpoints);
    }
}
