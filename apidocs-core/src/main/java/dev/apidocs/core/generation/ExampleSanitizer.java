package dev.apidocs.core.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.support.JsonSupport;
import java.util.Map;

/** Fact-checks LLM examples against the real schema: unknown fields go away, broken JSON is discarded. */
public final class ExampleSanitizer {

    public record Result(String json, boolean invalid) {
    }

    private static final int MAX_DEPTH = 12;

    private final Map<String, SchemaInfo> schemas;

    public ExampleSanitizer(Map<String, SchemaInfo> schemas) {
        this.schemas = schemas;
    }

    public Result sanitize(String example, TypeRef type) {
        if (example == null || example.isBlank() || type == null) {
            return new Result("", false);
        }
        JsonNode node;
        try {
            node = JsonSupport.mapper().readTree(stripFences(example));
        } catch (JsonProcessingException e) {
            return new Result("", true);
        }
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new Result("", true);
        }
        JsonNode cleaned = clean(node, type, 0);
        if (cleaned == null) {
            return new Result("", true);
        }
        return new Result(JsonSupport.toPrettyJson(cleaned).strip(), false);
    }

    /**
     * The node reduced to what the schema documents, or {@code null} when nothing of it can be kept: the shape
     * contradicts the type (an object where an array is expected and vice versa, null, a scalar) or every field or
     * element was undocumented. A caller drops such a value from its parent. Types that cannot be checked (scalars,
     * enums, maps, unknown schemas) keep the node as written.
     */
    private JsonNode clean(JsonNode node, TypeRef type, int depth) {
        if (depth > MAX_DEPTH) {
            return node;
        }
        if (type instanceof ObjectRef ref) {
            SchemaInfo schema = schemas.get(ref.schemaName());
            if (schema == null || schema.kind() != SchemaKind.OBJECT) {
                return node;
            }
            if (!node.isObject()) {
                return null;
            }
            ObjectNode out = JsonSupport.mapper().createObjectNode();
            for (FieldInfo field : schema.fields()) {
                if (node.has(field.name())) {
                    JsonNode value = clean(node.get(field.name()), field.type(), depth + 1);
                    if (value != null) {
                        out.set(field.name(), value);
                    }
                }
            }
            return out.isEmpty() && !schema.fields().isEmpty() ? null : out;
        }
        if (type instanceof ArrayOf array) {
            if (!node.isArray()) {
                return null;
            }
            ArrayNode out = JsonSupport.mapper().createArrayNode();
            for (JsonNode element : node) {
                JsonNode value = clean(element, array.items(), depth + 1);
                if (value != null) {
                    out.add(value);
                }
            }
            return out.isEmpty() && !node.isEmpty() ? null : out;
        }
        return node;
    }

    private static String stripFences(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            trimmed = newline < 0 ? "" : trimmed.substring(newline + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }
}
