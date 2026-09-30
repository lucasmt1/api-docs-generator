package dev.apidocs.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypeRefTest {

    @Test
    void displaysEveryKindOfType() {
        assertThat(new ScalarType(ScalarKind.STRING).display()).isEqualTo("string");
        assertThat(new ScalarType(ScalarKind.LONG).display()).isEqualTo("integer (int64)");
        assertThat(new ScalarType(ScalarKind.DATE_TIME).display()).isEqualTo("string (date-time)");
        assertThat(new ArrayOf(new ObjectRef("OrderItemRequest")).display()).isEqualTo("array<OrderItemRequest>");
        assertThat(new MapOf(new ScalarType(ScalarKind.INTEGER)).display()).isEqualTo("map<string, integer (int32)>");
        assertThat(new EnumRef("OrderStatus").display()).isEqualTo("OrderStatus");
        assertThat(new OpaqueType("JsonNode").display()).isEqualTo("object");
    }

    @Test
    void collectsReferencedSchemasThroughArraysAndMaps() {
        TypeRef type = new ArrayOf(new MapOf(new ObjectRef("Address")));

        assertThat(type.referencedSchemas()).containsExactly("Address");
        assertThat(new EnumRef("Status").referencedSchemas()).containsExactly("Status");
        assertThat(new ScalarType(ScalarKind.UUID).referencedSchemas()).isEmpty();
    }

    @Test
    void exposesOpenApiTypeAndFormat() {
        assertThat(ScalarKind.DECIMAL.openApiType()).isEqualTo("number");
        assertThat(ScalarKind.DECIMAL.openApiFormat()).isNull();
        assertThat(ScalarKind.UUID.openApiFormat()).isEqualTo("uuid");
    }
}
