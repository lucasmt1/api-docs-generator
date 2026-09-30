package dev.apidocs.core;

/** The run was cancelled by the user. */
public class CancelledException extends ApiDocsException {

    public CancelledException(String message) {
        super(message);
    }
}
