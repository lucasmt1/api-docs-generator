package dev.apidocs.core.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import dev.apidocs.core.analysis.HttpStatuses;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ParameterLocation;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.TypeRef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** OpenAPI 3.1: structure 100% from the code, descriptions from the narratives when available. */
public final class OpenApiDocument {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
            .build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String JSON = "application/json";

    public String render(ApiModel model, NarrativeSet narratives) {
        ObjectNode root = NODES.objectNode();
        root.put("openapi", "3.1.0");
        ObjectNode info = root.putObject("info");
        info.put("title", model.project().name());
        info.put("version", model.project().version().isBlank() ? "0.0.0" : model.project().version());
        String description = narratives.technicalDoc().map(TechnicalDocNarrative::overview)
                .filter(text -> !text.isBlank()).orElse(model.project().description());
        if (!description.isBlank()) {
            info.put("description", description);
        }

        ArrayNode tags = root.putArray("tags");
        ObjectNode paths = root.putObject("paths");
        for (ControllerInfo controller : model.controllers()) {
            ObjectNode tag = tags.addObject().put("name", controller.name());
            String tagDescription = narratives.controller(controller.name())
                    .map(narrative -> narrative.controllerSummary()).filter(text -> !text.isBlank())
                    .orElse(controller.description());
            if (!tagDescription.isBlank()) {
                tag.put("description", tagDescription);
            }
            for (EndpointInfo endpoint : controller.endpoints()) {
                ObjectNode pathItem = paths.has(endpoint.path())
                        ? (ObjectNode) paths.get(endpoint.path())
                        : paths.putObject(endpoint.path());
                String verb = endpoint.method() == HttpMethod.ANY ? "get" : endpoint.method().name().toLowerCase(Locale.ROOT);
                if (!pathItem.has(verb)) {
                    pathItem.set(verb, operation(controller, endpoint,
                            narratives.endpoint(controller.name(), endpoint.id())));
                }
            }
        }

        ObjectNode schemas = root.putObject("components").putObject("schemas");
        model.schemas().forEach(schema -> schemas.set(schema.name(), schema(schema)));
        try {
            return YAML.writeValueAsString(root).replace("\r\n", "\n");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write OpenAPI document", e);
        }
    }

    private ObjectNode operation(ControllerInfo controller, EndpointInfo endpoint, Optional<EndpointNarrative> narrative) {
        ObjectNode operation = NODES.objectNode();
        operation.putArray("tags").add(controller.name());
        operation.put("operationId", endpoint.id().replace('#', '_'));
        String summary = narrative.map(EndpointNarrative::title).filter(text -> !text.isBlank()).orElse(endpoint.summary());
        if (!summary.isBlank()) {
            operation.put("summary", summary);
        }
        String description = narrative.map(EndpointNarrative::description).filter(text -> !text.isBlank())
                .orElse(endpoint.description());
        if (!description.isBlank()) {
            operation.put("description", description);
        }
        if (endpoint.deprecated()) {
            operation.put("deprecated", true);
        }
        if (endpoint.method() == HttpMethod.ANY) {
            operation.putArray("x-http-methods").add("ANY");
        }
        if (!endpoint.security().isBlank()) {
            operation.put("x-security-expression", endpoint.security());
        }
        if (!endpoint.parameters().isEmpty()) {
            ArrayNode parameters = operation.putArray("parameters");
            endpoint.parameters().forEach(parameter -> parameters.add(parameter(parameter)));
        }
        if (endpoint.requestBody() != null) {
            ObjectNode body = operation.putObject("requestBody");
            body.put("required", true);
            body.putObject("content")
                    .putObject(endpoint.consumes().isEmpty() ? JSON : endpoint.consumes().get(0))
                    .set("schema", typeSchema(endpoint.requestBody().type()));
        }
        ObjectNode responses = operation.putObject("responses");
        ObjectNode success = responses.putObject(String.valueOf(endpoint.response().status()));
        success.put("description", HttpStatuses.reason(endpoint.response().status()));
        if (endpoint.response().hasBody()) {
            success.putObject("content")
                    .putObject(endpoint.produces().isEmpty() ? JSON : endpoint.produces().get(0))
                    .set("schema", typeSchema(endpoint.response().body()));
        }
        Map<Integer, List<String>> errors = new TreeMap<>();
        for (ErrorResponse error : endpoint.errors()) {
            errors.computeIfAbsent(error.status(), status -> new ArrayList<>()).add(error.exception());
        }
        errors.forEach((status, exceptions) -> {
            String when = narrative.flatMap(n -> n.whenFor(status))
                    .orElse(HttpStatuses.reason(status) + ": " + String.join(", ", exceptions));
            responses.putObject(String.valueOf(status)).put("description", when);
        });
        return operation;
    }

    private ObjectNode parameter(ParameterInfo parameter) {
        ObjectNode node = NODES.objectNode();
        node.put("name", parameter.name());
        node.put("in", parameter.in().name().toLowerCase(Locale.ROOT));
        node.put("required", parameter.in() == ParameterLocation.PATH || parameter.required());
        ObjectNode schema = typeSchema(parameter.type());
        applyConstraints(schema, parameter.constraints(), parameter.type());
        if (!parameter.defaultValue().isBlank()) {
            putDefault(schema, parameter.defaultValue(), parameter.type());
        }
        node.set("schema", schema);
        return node;
    }

    private ObjectNode schema(SchemaInfo schema) {
        ObjectNode node = NODES.objectNode();
        if (schema.kind() == SchemaKind.ENUM) {
            node.put("type", "string");
            if (!schema.description().isBlank()) {
                node.put("description", schema.description());
            }
            ArrayNode values = node.putArray("enum");
            schema.enumValues().forEach(values::add);
            return node;
        }
        node.put("type", "object");
        if (!schema.description().isBlank()) {
            node.put("description", schema.description());
        }
        List<String> required = schema.fields().stream().filter(FieldInfo::required).map(FieldInfo::name).toList();
        if (!required.isEmpty()) {
            ArrayNode requiredNode = node.putArray("required");
            required.forEach(requiredNode::add);
        }
        ObjectNode properties = node.putObject("properties");
        for (FieldInfo field : schema.fields()) {
            ObjectNode property = typeSchema(field.type());
            applyConstraints(property, field.constraints(), field.type());
            if (!field.description().isBlank()) {
                property.put("description", field.description());
            }
            properties.set(field.name(), property);
        }
        return node;
    }

    private ObjectNode typeSchema(TypeRef type) {
        ObjectNode node = NODES.objectNode();
        switch (type) {
            case ScalarType scalar -> {
                node.put("type", scalar.kind().openApiType());
                if (scalar.kind().openApiFormat() != null) {
                    node.put("format", scalar.kind().openApiFormat());
                }
            }
            case ArrayOf array -> {
                node.put("type", "array");
                node.set("items", typeSchema(array.items()));
            }
            case MapOf map -> {
                node.put("type", "object");
                node.set("additionalProperties", typeSchema(map.values()));
            }
            case ObjectRef object -> node.put("$ref", "#/components/schemas/" + object.schemaName());
            case EnumRef enumRef -> node.put("$ref", "#/components/schemas/" + enumRef.schemaName());
            case OpaqueType opaque -> {
                node.put("type", "object");
                node.put("x-java-type", opaque.javaType());
            }
        }
        return node;
    }

    private static void applyConstraints(ObjectNode node, List<Constraint> constraints, TypeRef type) {
        boolean array = type instanceof ArrayOf;
        boolean string = type instanceof ScalarType scalar && scalar.kind().openApiType().equals("string");
        for (Constraint constraint : constraints) {
            switch (constraint.name()) {
                case "Size" -> {
                    constraint.attribute("min").ifPresent(v -> putNumber(node, array ? "minItems" : string ? "minLength" : "minProperties", v));
                    constraint.attribute("max").ifPresent(v -> putNumber(node, array ? "maxItems" : string ? "maxLength" : "maxProperties", v));
                }
                case "Min", "DecimalMin" -> constraint.attribute("value").ifPresent(v -> putNumber(node,
                        constraint.attribute("inclusive").filter("false"::equals).isPresent() ? "exclusiveMinimum" : "minimum", v));
                case "Max", "DecimalMax" -> constraint.attribute("value").ifPresent(v -> putNumber(node,
                        constraint.attribute("inclusive").filter("false"::equals).isPresent() ? "exclusiveMaximum" : "maximum", v));
                case "Positive" -> putNumber(node, "exclusiveMinimum", "0");
                case "PositiveOrZero" -> putNumber(node, "minimum", "0");
                case "Negative" -> putNumber(node, "exclusiveMaximum", "0");
                case "NegativeOrZero" -> putNumber(node, "maximum", "0");
                case "Email" -> node.put("format", "email");
                case "Pattern" -> constraint.attribute("regexp").ifPresent(v -> node.put("pattern", v));
                case "NotBlank" -> {
                    if (string && !node.has("minLength")) {
                        node.put("minLength", 1);
                    }
                }
                case "NotEmpty" -> {
                    if (array && !node.has("minItems")) {
                        node.put("minItems", 1);
                    } else if (string && !node.has("minLength")) {
                        node.put("minLength", 1);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Non-numeric constraint values (e.g. constants) are left out. */
    private static void putNumber(ObjectNode node, String key, String value) {
        number(value).ifPresent(number -> node.set(key, number));
    }

    private static void putDefault(ObjectNode schema, String value, TypeRef type) {
        if (type instanceof ScalarType scalar) {
            switch (scalar.kind().openApiType()) {
                case "integer", "number" -> {
                    Optional<JsonNode> number = number(value);
                    if (number.isPresent()) {
                        schema.set("default", number.get());
                        return;
                    }
                    // not numeric: fall back to a string default
                }
                case "boolean" -> {
                    schema.put("default", Boolean.parseBoolean(value));
                    return;
                }
                default -> {
                }
            }
        }
        schema.put("default", value);
    }

    /** A whole number becomes a long, a fractional one a decimal; empty when the text is not numeric. */
    private static Optional<JsonNode> number(String value) {
        try {
            BigDecimal number = new BigDecimal(value);
            return Optional.of(number.scale() <= 0 ? NODES.numberNode(number.longValueExact()) : NODES.numberNode(number));
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }
}
