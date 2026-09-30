package dev.apidocs.core.extraction;

import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ServiceInfo;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Computes the possible error responses of each endpoint (fed into Business Rules and the API Reference). */
public final class ErrorResolver {

    public static final String BODY_VALIDATION = "MethodArgumentNotValidException";
    public static final String PARAMETER_VALIDATION = "HandlerMethodValidationException";

    private final ExceptionStatusResolver statuses;
    private final Map<String, ServiceInfo> services = new LinkedHashMap<>();

    public ErrorResolver(ExceptionStatusResolver statuses, List<ServiceInfo> services) {
        this.statuses = statuses;
        services.forEach(service -> this.services.putIfAbsent(service.name(), service));
    }

    public ControllerInfo resolve(ControllerInfo controller) {
        return controller.withEndpoints(controller.endpoints().stream().map(this::resolve).toList());
    }

    private EndpointInfo resolve(EndpointInfo endpoint) {
        Map<String, ErrorResponse> errors = new LinkedHashMap<>();
        if (endpoint.requestBody() != null && endpoint.requestBody().validated()) {
            errors.put(BODY_VALIDATION, new ErrorResponse(statuses.statusOf(BODY_VALIDATION).orElse(400), BODY_VALIDATION));
        }
        if (endpoint.parameters().stream().anyMatch(parameter -> !parameter.constraints().isEmpty())) {
            errors.put(PARAMETER_VALIDATION,
                    new ErrorResponse(statuses.statusOf(PARAMETER_VALIDATION).orElse(400), PARAMETER_VALIDATION));
        }
        Set<String> exceptions = new LinkedHashSet<>(endpoint.thrownExceptions());
        for (MethodRef call : endpoint.serviceCalls()) {
            ServiceInfo service = services.get(call.typeName());
            if (service != null) {
                service.method(call.methodName()).ifPresent(method -> exceptions.addAll(method.thrownExceptions()));
            }
        }
        for (String exception : exceptions) {
            OptionalInt status = statuses.statusOf(exception);
            if (status.isPresent()) {
                errors.putIfAbsent(exception, new ErrorResponse(status.getAsInt(), exception));
            } else if (statuses.isProjectType(exception)) {
                errors.putIfAbsent(exception, new ErrorResponse(500, exception));
            }
        }
        return endpoint.withErrors(errors.values().stream()
                .sorted(Comparator.comparingInt(ErrorResponse::status).thenComparing(ErrorResponse::exception))
                .toList());
    }
}
