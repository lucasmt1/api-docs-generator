package dev.apidocs.core.extraction;

import dev.apidocs.core.analysis.ConstantResolver;
import dev.apidocs.core.analysis.ParseOutcome;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ProjectInfo;
import dev.apidocs.core.model.RepositoryInfo;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.model.Warnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Runs the Code Extractor over a parsed project and builds the {@link ApiModel}. */
public final class ModelAssembler {

    public ApiModel assemble(ProjectInfo project, ParseOutcome parsed, List<Warning> loadWarnings) {
        TypeIndex index = TypeIndex.build(parsed.units());
        SchemaRegistry registry = new SchemaRegistry();
        TypeResolver resolver = new TypeResolver(index, registry);
        List<Warning> warnings = new ArrayList<>(loadWarnings);
        warnings.addAll(parsed.warnings());

        List<RepositoryInfo> repositories = sorted(new RepositoryExtractor(index).extract(), RepositoryInfo::name);
        Set<String> repositoryNames = repositories.stream().map(RepositoryInfo::name).collect(Collectors.toSet());
        Set<String> serviceNames = ServiceExtractor.serviceNames(index);
        List<ServiceInfo> services = sorted(new ServiceExtractor(index, serviceNames, repositoryNames).extract(),
                ServiceInfo::name);
        List<ControllerInfo> controllers = new ControllerExtractor(index, resolver, new ConstantResolver(index),
                project.contextPath(), serviceNames).extract(warnings);
        List<EntityInfo> entities = sorted(new EntityExtractor(index, resolver).extract(), EntityInfo::name);
        Set<String> entityNames = entities.stream().map(EntityInfo::qualifiedName).collect(Collectors.toSet());
        List<SchemaInfo> schemas = new SchemaExtractor(index, resolver, registry, entityNames).extractAll();
        warnings.addAll(registry.warnings());
        List<ExceptionMapping> mappings = new ExceptionMappingExtractor(index).extract(warnings);

        ErrorResolver errors = new ErrorResolver(new ExceptionStatusResolver(index, mappings), services);
        List<ControllerInfo> resolved = controllers.stream()
                .map(errors::resolve)
                .map(ModelAssembler::sortEndpoints)
                .sorted(Comparator.comparing(ControllerInfo::name))
                .toList();
        warnings.addAll(opaqueTypeWarnings(resolved, schemas));
        return new ApiModel(project, resolved, schemas, entities, services, repositories, mappings,
                Warnings.sortedDistinct(warnings));
    }

    private static <T> List<T> sorted(List<T> items, Function<T, String> key) {
        return items.stream().sorted(Comparator.comparing(key)).toList();
    }

    private static ControllerInfo sortEndpoints(ControllerInfo controller) {
        return controller.withEndpoints(controller.endpoints().stream()
                .sorted(Comparator.comparing(EndpointInfo::path).thenComparing(EndpointInfo::method))
                .toList());
    }

    private static List<Warning> opaqueTypeWarnings(List<ControllerInfo> controllers, List<SchemaInfo> schemas) {
        List<Warning> warnings = new ArrayList<>();
        for (ControllerInfo controller : controllers) {
            for (EndpointInfo endpoint : controller.endpoints()) {
                for (ParameterInfo parameter : endpoint.parameters()) {
                    opaque(parameter.type(), endpoint.id() + " parameter " + parameter.name(), warnings);
                }
                if (endpoint.requestBody() != null) {
                    opaque(endpoint.requestBody().type(), endpoint.id() + " request body", warnings);
                }
                if (endpoint.response().hasBody()) {
                    opaque(endpoint.response().body(), endpoint.id() + " response", warnings);
                }
            }
        }
        for (SchemaInfo schema : schemas) {
            for (FieldInfo field : schema.fields()) {
                opaque(field.type(), schema.name() + "." + field.name(), warnings);
            }
        }
        return warnings;
    }

    private static void opaque(TypeRef type, String location, List<Warning> warnings) {
        switch (type) {
            case OpaqueType opaque -> warnings.add(new Warning("OPAQUE_TYPE",
                    "Type " + opaque.javaType() + " is documented as a generic object", location));
            case ArrayOf array -> opaque(array.items(), location, warnings);
            case MapOf map -> opaque(map.values(), location, warnings);
            default -> {
            }
        }
    }
}
