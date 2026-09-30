package dev.apidocs.core;

/** The project could not be analyzed (missing directory, no controllers, limits exceeded). */
public class AnalysisException extends ApiDocsException {

    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
