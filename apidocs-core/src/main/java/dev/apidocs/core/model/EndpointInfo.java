package dev.apidocs.core.model;

import java.util.List;

public record EndpointInfo(
        String id,
        HttpMethod method,
        String path,
        String javaMethod,
        List<ParameterInfo> parameters,
        RequestBodyInfo requestBody,
        ResponseInfo response,
        List<ErrorResponse> errors,
        List<String> thrownExceptions,
        String security,
        boolean deprecated,
        String summary,
        String description,
        List<String> consumes,
        List<String> produces,
        List<MethodRef> serviceCalls) {

    public EndpointInfo {
        parameters = List.copyOf(parameters);
        errors = List.copyOf(errors);
        thrownExceptions = List.copyOf(thrownExceptions);
        security = security == null ? "" : security;
        summary = summary == null ? "" : summary;
        description = description == null ? "" : description;
        consumes = List.copyOf(consumes);
        produces = List.copyOf(produces);
        serviceCalls = List.copyOf(serviceCalls);
    }

    public EndpointInfo withErrors(List<ErrorResponse> newErrors) {
        return new EndpointInfo(id, method, path, javaMethod, parameters, requestBody, response, newErrors,
                thrownExceptions, security, deprecated, summary, description, consumes, produces, serviceCalls);
    }
}
