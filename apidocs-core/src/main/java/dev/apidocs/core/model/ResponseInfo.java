package dev.apidocs.core.model;

/** Success response. {@code body} is {@code null} when the endpoint returns no body. */
public record ResponseInfo(int status, TypeRef body) {

    public boolean hasBody() {
        return body != null;
    }
}
