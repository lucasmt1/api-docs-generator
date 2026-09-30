package dev.apidocs.core;

/** Invalid configuration, arguments or missing credentials. */
public class ConfigException extends ApiDocsException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
