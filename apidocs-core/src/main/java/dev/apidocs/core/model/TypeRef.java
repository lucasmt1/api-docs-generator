package dev.apidocs.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.LinkedHashSet;
import java.util.Set;

/** Type of a field, parameter or body as it appears in the documentation. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ScalarType.class, name = "scalar"),
        @JsonSubTypes.Type(value = ArrayOf.class, name = "array"),
        @JsonSubTypes.Type(value = MapOf.class, name = "map"),
        @JsonSubTypes.Type(value = ObjectRef.class, name = "object"),
        @JsonSubTypes.Type(value = EnumRef.class, name = "enum"),
        @JsonSubTypes.Type(value = OpaqueType.class, name = "opaque")})
public sealed interface TypeRef permits ScalarType, ArrayOf, MapOf, ObjectRef, EnumRef, OpaqueType {

    /** Human readable form used in tables, e.g. {@code array<OrderItemRequest>}. */
    default String display() {
        return switch (this) {
            case ScalarType scalar -> scalar.kind().display();
            case ArrayOf array -> "array<" + array.items().display() + ">";
            case MapOf map -> "map<string, " + map.values().display() + ">";
            case ObjectRef object -> object.schemaName();
            case EnumRef enumRef -> enumRef.schemaName();
            case OpaqueType opaque -> "object";
        };
    }

    /** Names of the schemas this type points to, directly or through arrays and maps. */
    default Set<String> referencedSchemas() {
        Set<String> names = new LinkedHashSet<>();
        collect(this, names);
        return names;
    }

    private static void collect(TypeRef type, Set<String> names) {
        switch (type) {
            case ArrayOf array -> collect(array.items(), names);
            case MapOf map -> collect(map.values(), names);
            case ObjectRef object -> names.add(object.schemaName());
            case EnumRef enumRef -> names.add(enumRef.schemaName());
            case ScalarType scalar -> {
            }
            case OpaqueType opaque -> {
            }
        }
    }
}
