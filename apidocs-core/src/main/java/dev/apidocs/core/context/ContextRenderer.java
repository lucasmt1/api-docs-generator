package dev.apidocs.core.context;

import dev.apidocs.core.generation.ArchitectureAlert;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.EntityField;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ProjectInfo;
import dev.apidocs.core.model.Relationship;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.ServiceMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Renders the three inputs of the AI stage (Structure & Logic, Data Schemas, Business Rules) as compact text. */
public final class ContextRenderer {

    private static final String NONE = "(none)";

    /** Any spelling of the closing tag: other case, or whitespace around the slash and the name. */
    private static final Pattern CLOSING_SOURCE_TAG = Pattern.compile("(?i)<\\s*/\\s*source_code\\s*>");
    private static final String NEUTRALIZED_CLOSING_TAG = Matcher.quoteReplacement("<\\/source_code>");

    public String projectOverview(ApiModel model) {
        ProjectInfo project = model.project();
        List<String> lines = new ArrayList<>();
        lines.add("- Name: " + project.name()
                + (project.applicationName().isBlank() ? "" : " (application " + project.applicationName() + ")"));
        if (!project.description().isBlank()) {
            lines.add("- Description: " + project.description());
        }
        lines.add("- Stack: Java " + orUnknown(project.javaVersion()) + ", Spring Boot "
                + orUnknown(project.springBootVersion()) + ", build " + project.buildTool());
        if (!project.dependencies().isEmpty()) {
            lines.add("- Dependencies: " + String.join(", ", project.dependencies()));
        }
        lines.add("- Size: " + model.controllers().size() + " controllers, " + model.endpointCount() + " endpoints, "
                + model.schemas().size() + " schemas, " + model.entities().size() + " entities, "
                + model.services().size() + " services");
        return String.join("\n", lines);
    }

    public String endpoints(ControllerInfo controller) {
        StringBuilder out = new StringBuilder("### ").append(controller.name())
                .append(" (base path ").append(controller.basePath()).append(")\n");
        if (!controller.description().isBlank()) {
            out.append(controller.description()).append('\n');
        }
        controller.endpoints().forEach(endpoint -> out.append(endpointLine(endpoint)).append('\n'));
        return out.toString().strip();
    }

    public String structure(ApiModel model) {
        return model.controllers().stream()
                .map(controller -> "- " + controller.name() + ": " + controller.endpoints().stream()
                        .map(endpoint -> endpoint.method() + " " + endpoint.path())
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
    }

    public String schemas(Collection<SchemaInfo> schemas) {
        if (schemas.isEmpty()) {
            return NONE;
        }
        return schemas.stream().map(this::schema).collect(Collectors.joining("\n\n"));
    }

    public String entities(Collection<EntityInfo> entities) {
        if (entities.isEmpty()) {
            return NONE;
        }
        return entities.stream().map(this::entity).collect(Collectors.joining("\n\n"));
    }

    public String serviceMethods(List<ServiceMethodRef> methods) {
        if (methods.isEmpty()) {
            return NONE;
        }
        return methods.stream().map(ContextRenderer::sourceBlock).collect(Collectors.joining("\n\n"));
    }

    public String exceptionMappings(List<ExceptionMapping> mappings) {
        if (mappings.isEmpty()) {
            return NONE;
        }
        return mappings.stream().map(m -> "- " + m.exception() + " -> " + m.status()).collect(Collectors.joining("\n"));
    }

    public String components(ApiModel model) {
        List<String> lines = new ArrayList<>();
        for (ControllerInfo controller : model.controllers()) {
            lines.add("- Controller " + controller.name() + " depends on: " + joinOrNone(controller.dependencies()));
        }
        for (ServiceInfo service : model.services()) {
            lines.add("- Service " + service.name() + " depends on: " + joinOrNone(service.dependencies()));
        }
        model.repositories().forEach(repository -> lines.add("- Repository " + repository.name() + " manages entity "
                + (repository.entity().isBlank() ? "unknown" : repository.entity())));
        model.entities().forEach(entity -> lines.add("- Entity " + entity.name() + " (table " + entity.table() + ")"));
        return lines.isEmpty() ? NONE : String.join("\n", lines);
    }

    public String metrics(ApiModel model) {
        return String.join("\n",
                "- controllers: " + model.controllers().size(),
                "- endpoints: " + model.endpointCount(),
                "- services: " + model.services().size(),
                "- repositories: " + model.repositories().size(),
                "- entities: " + model.entities().size(),
                "- schemas: " + model.schemas().size());
    }

    public String alerts(List<ArchitectureAlert> alerts) {
        if (alerts.isEmpty()) {
            return NONE;
        }
        return alerts.stream()
                .map(alert -> "- [" + alert.code() + "] " + alert.subject()
                        + (alert.arguments().isEmpty() ? "" : ": " + String.join(", ", alert.arguments())))
                .collect(Collectors.joining("\n"));
    }

    String endpointLine(EndpointInfo endpoint) {
        List<String> parts = new ArrayList<>();
        parts.add("- [" + endpoint.id() + "] " + endpoint.method() + " " + endpoint.path() + " -> "
                + endpoint.response().status() + " "
                + (endpoint.response().hasBody() ? endpoint.response().body().display() : "no body"));
        if (!endpoint.parameters().isEmpty()) {
            parts.add("params: " + endpoint.parameters().stream().map(ContextRenderer::parameter)
                    .collect(Collectors.joining(", ")));
        }
        if (endpoint.requestBody() != null) {
            parts.add("body: " + endpoint.requestBody().type().display()
                    + (endpoint.requestBody().validated() ? " (@Valid)" : ""));
        }
        if (!endpoint.errors().isEmpty()) {
            parts.add("errors: " + endpoint.errors().stream().map(e -> e.status() + " " + e.exception())
                    .collect(Collectors.joining(", ")));
        }
        if (!endpoint.security().isBlank()) {
            parts.add("security: " + endpoint.security());
        }
        if (!endpoint.serviceCalls().isEmpty()) {
            parts.add("calls: " + endpoint.serviceCalls().stream().map(c -> c.typeName() + "." + c.methodName())
                    .collect(Collectors.joining(", ")));
        }
        if (endpoint.deprecated()) {
            parts.add("deprecated");
        }
        if (!endpoint.summary().isBlank()) {
            parts.add("summary: " + endpoint.summary());
        }
        if (!endpoint.description().isBlank()) {
            parts.add("javadoc: " + endpoint.description());
        }
        return String.join("; ", parts);
    }

    private static String parameter(ParameterInfo parameter) {
        return parameter.name() + " " + parameter.in().name().toLowerCase(Locale.ROOT) + " " + parameter.type().display()
                + (parameter.required() ? " required" : "")
                + (parameter.defaultValue().isBlank() ? "" : " default=" + parameter.defaultValue())
                + constraints(parameter.constraints());
    }

    private String schema(SchemaInfo schema) {
        StringBuilder out = new StringBuilder("#### ").append(schema.name());
        if (schema.kind() == SchemaKind.ENUM) {
            out.append(" (enum)");
        }
        if (schema.entity()) {
            out.append(" (JPA entity)");
        }
        if (!schema.description().isBlank()) {
            out.append('\n').append(schema.description());
        }
        if (schema.kind() == SchemaKind.ENUM) {
            out.append("\nvalues: ").append(String.join(", ", schema.enumValues()));
        } else {
            for (FieldInfo field : schema.fields()) {
                out.append("\n- ").append(field.name()).append(": ").append(field.type().display())
                        .append(field.required() ? ", required" : "")
                        .append(constraints(field.constraints()))
                        .append(field.description().isBlank() ? "" : " — " + field.description());
            }
        }
        return out.toString();
    }

    private String entity(EntityInfo entity) {
        StringBuilder out = new StringBuilder("#### ").append(entity.name()).append(" (table ").append(entity.table()).append(')');
        for (EntityField field : entity.fields()) {
            out.append("\n- ").append(field.name()).append(": ").append(field.type().display())
                    .append(field.id() ? " PK" : "")
                    .append(field.unique() ? " unique" : "")
                    .append(!field.nullable() && !field.id() ? " not null" : "")
                    .append(field.length() > 0 ? " length=" + field.length() : "");
        }
        for (Relationship relationship : entity.relationships()) {
            out.append("\n- relation ").append(relationship.field()).append(": ").append(relationship.kind())
                    .append(" -> ").append(relationship.target())
                    .append(relationship.mappedBy().isBlank() ? "" : " (mappedBy " + relationship.mappedBy() + ")");
        }
        return out.toString();
    }

    private static String sourceBlock(ServiceMethodRef reference) {
        ServiceMethod method = reference.method();
        StringBuilder inner = new StringBuilder("// ").append(method.signature());
        if (method.transactional()) {
            inner.append(method.readOnly() ? " @Transactional(readOnly)" : " @Transactional");
        }
        if (!method.thrownExceptions().isEmpty()) {
            inner.append("\n// throws: ").append(String.join(", ", method.thrownExceptions()));
        }
        if (!method.description().isBlank()) {
            inner.append("\n// javadoc: ").append(method.description());
        }
        inner.append('\n').append(method.body());
        // Everything between the wrapper tags comes from the repository, so the block is neutralized as a whole.
        return "<source_code file=\"" + reference.service() + '#' + method.name() + "\">\n"
                + CLOSING_SOURCE_TAG.matcher(inner).replaceAll(NEUTRALIZED_CLOSING_TAG)
                + "\n</source_code>";
    }

    private static String constraints(List<Constraint> constraints) {
        return constraints.isEmpty() ? ""
                : " " + constraints.stream().map(Constraint::describe).collect(Collectors.joining(" "));
    }

    private static String joinOrNone(List<String> values) {
        return values.isEmpty() ? "nothing" : String.join(", ", values);
    }

    private static String orUnknown(String value) {
        return value.isBlank() ? "unknown" : value;
    }
}
