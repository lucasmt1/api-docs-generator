package dev.apidocs.core.context;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.SchemaGraph;
import dev.apidocs.core.model.SchemaInfo;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SliceBuilder {

    public ControllerSlice slice(ApiModel model, ControllerInfo controller) {
        Map<String, SchemaInfo> byName = model.schemasByName();
        Set<String> roots = new LinkedHashSet<>();
        Set<ServiceMethodRef> methods = new LinkedHashSet<>();
        for (EndpointInfo endpoint : controller.endpoints()) {
            endpoint.parameters().forEach(parameter -> roots.addAll(parameter.type().referencedSchemas()));
            if (endpoint.requestBody() != null) {
                roots.addAll(endpoint.requestBody().type().referencedSchemas());
            }
            if (endpoint.response().hasBody()) {
                roots.addAll(endpoint.response().body().referencedSchemas());
            }
            for (MethodRef call : endpoint.serviceCalls()) {
                model.service(call.typeName())
                        .flatMap(service -> service.method(call.methodName())
                                .map(method -> new ServiceMethodRef(service.name(), method)))
                        .ifPresent(methods::add);
            }
        }
        List<SchemaInfo> schemas = SchemaGraph.closure(roots, byName).stream()
                .map(byName::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SchemaInfo::name))
                .toList();
        return new ControllerSlice(controller, schemas, List.copyOf(methods), model.exceptionMappings());
    }
}
