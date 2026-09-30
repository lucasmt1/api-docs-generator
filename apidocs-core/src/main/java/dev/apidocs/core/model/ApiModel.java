package dev.apidocs.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything extracted from the analyzed project: the three inputs of the AI stage. */
public record ApiModel(
        ProjectInfo project,
        List<ControllerInfo> controllers,
        List<SchemaInfo> schemas,
        List<EntityInfo> entities,
        List<ServiceInfo> services,
        List<RepositoryInfo> repositories,
        List<ExceptionMapping> exceptionMappings,
        List<Warning> warnings) {

    public ApiModel {
        controllers = List.copyOf(controllers);
        schemas = List.copyOf(schemas);
        entities = List.copyOf(entities);
        services = List.copyOf(services);
        repositories = List.copyOf(repositories);
        exceptionMappings = List.copyOf(exceptionMappings);
        warnings = List.copyOf(warnings);
    }

    public Map<String, SchemaInfo> schemasByName() {
        Map<String, SchemaInfo> byName = new LinkedHashMap<>();
        schemas.forEach(schema -> byName.put(schema.name(), schema));
        return byName;
    }

    public Optional<ServiceInfo> service(String name) {
        return services.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    public int endpointCount() {
        return controllers.stream().mapToInt(c -> c.endpoints().size()).sum();
    }
}
