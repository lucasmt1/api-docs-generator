package dev.apidocs.core.model;

/** Primitive-like types and their OpenAPI representation. */
public enum ScalarKind {
    STRING("string", null),
    INTEGER("integer", "int32"),
    LONG("integer", "int64"),
    FLOAT("number", "float"),
    DOUBLE("number", "double"),
    DECIMAL("number", "decimal"),
    NUMBER("number", null),
    BOOLEAN("boolean", null),
    DATE("string", "date"),
    DATE_TIME("string", "date-time"),
    TIME("string", "time"),
    UUID("string", "uuid"),
    BINARY("string", "binary");

    private final String openApiType;
    private final String openApiFormat;

    ScalarKind(String openApiType, String openApiFormat) {
        this.openApiType = openApiType;
        this.openApiFormat = openApiFormat;
    }

    public String openApiType() {
        return openApiType;
    }

    /** OpenAPI format, or {@code null} when the type has none. */
    public String openApiFormat() {
        return openApiFormat;
    }

    public String display() {
        return openApiFormat == null ? openApiType : openApiType + " (" + openApiFormat + ")";
    }
}
