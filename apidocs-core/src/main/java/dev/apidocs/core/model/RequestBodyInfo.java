package dev.apidocs.core.model;

/** Request body; {@code validated} is true when annotated with {@code @Valid}/{@code @Validated}. */
public record RequestBodyInfo(TypeRef type, boolean validated) {
}
