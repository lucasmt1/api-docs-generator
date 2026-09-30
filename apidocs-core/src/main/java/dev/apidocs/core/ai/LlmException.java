package dev.apidocs.core.ai;

import dev.apidocs.core.ApiDocsException;

/** A provider call failed after the adapter's own retries. */
public class LlmException extends ApiDocsException {

    private final boolean retryable;

    public LlmException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
