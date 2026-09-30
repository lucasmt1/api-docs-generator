package dev.apidocs.core.model;

import java.util.List;

public record ServiceMethod(
        String name,
        String signature,
        String body,
        boolean transactional,
        boolean readOnly,
        List<String> thrownExceptions,
        List<MethodRef> calls,
        String description) {

    public ServiceMethod {
        thrownExceptions = List.copyOf(thrownExceptions);
        calls = List.copyOf(calls);
        description = description == null ? "" : description;
    }

    public ServiceMethod withBody(String newBody) {
        return new ServiceMethod(name, signature, newBody, transactional, readOnly, thrownExceptions, calls, description);
    }
}
