package dev.apidocs.core.context;

import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.SchemaInfo;
import java.util.List;

/** Everything one controller-level LLM call needs, and nothing more. */
public record ControllerSlice(
        ControllerInfo controller,
        List<SchemaInfo> schemas,
        List<ServiceMethodRef> serviceMethods,
        List<ExceptionMapping> exceptionMappings) {

    public ControllerSlice {
        schemas = List.copyOf(schemas);
        serviceMethods = List.copyOf(serviceMethods);
        exceptionMappings = List.copyOf(exceptionMappings);
    }
}
