package dev.apidocs.core.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.support.JsonSupport;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.List;

/** Strict JSON Schema (every property required, no extra properties) derived from a record. */
public final class RecordSchemaGenerator {

    private RecordSchemaGenerator() {
    }

    public static ObjectNode schemaFor(Class<? extends Record> type) {
        return schemaOf(type);
    }

    private static ObjectNode schemaOf(Type type) {
        ObjectNode node = JsonSupport.mapper().createObjectNode();
        if (type == String.class) {
            node.put("type", "string");
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            node.put("type", "integer");
        } else if (type == boolean.class || type == Boolean.class) {
            node.put("type", "boolean");
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            node.put("type", "number");
        } else if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
            node.put("type", "array");
            node.set("items", schemaOf(parameterized.getActualTypeArguments()[0]));
        } else if (type instanceof Class<?> recordClass && recordClass.isRecord()) {
            node.put("type", "object");
            ObjectNode properties = node.putObject("properties");
            ArrayNode required = node.putArray("required");
            for (RecordComponent component : recordClass.getRecordComponents()) {
                ObjectNode property = schemaOf(component.getGenericType());
                Describe describe = component.getAnnotation(Describe.class);
                if (describe != null) {
                    property.put("description", describe.value());
                }
                properties.set(component.getName(), property);
                required.add(component.getName());
            }
            node.put("additionalProperties", false);
        } else {
            throw new IllegalArgumentException("Unsupported type in LLM schema: " + type.getTypeName());
        }
        return node;
    }
}
