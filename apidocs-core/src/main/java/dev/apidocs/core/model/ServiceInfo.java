package dev.apidocs.core.model;

import java.util.List;
import java.util.Optional;

public record ServiceInfo(String name, String qualifiedName, List<String> dependencies, List<ServiceMethod> methods) {

    public ServiceInfo {
        dependencies = List.copyOf(dependencies);
        methods = List.copyOf(methods);
    }

    public Optional<ServiceMethod> method(String methodName) {
        return methods.stream().filter(m -> m.name().equals(methodName)).findFirst();
    }
}
