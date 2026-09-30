package dev.apidocs.core.model;

public enum HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS,
    /** {@code @RequestMapping} without {@code method}: matches every verb. */
    ANY
}
