package dev.apidocs.core.ai;

import dev.apidocs.core.ApiDocsException;

/**
 * A provider call failed after the adapter's own retries.
 *
 * <p>The message is a safe summary (kind of failure, HTTP status) that may end up in the generated documents, so it
 * never quotes the provider and never names the endpoint's host, which may be internal. The host and what the
 * provider or its SDK said, redacted, are the {@link #detail()}, which is only logged to the console: provider errors
 * may name accounts, projects or organizations.
 */
public class LlmException extends ApiDocsException {

    private final boolean retryable;
    private final String detail;

    public LlmException(String message, boolean retryable) {
        this(message, "", retryable);
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        this(message, "", retryable, cause);
    }

    public LlmException(String message, String detail, boolean retryable) {
        super(message);
        this.retryable = retryable;
        this.detail = detail == null ? "" : detail;
    }

    public LlmException(String message, String detail, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.detail = detail == null ? "" : detail;
    }

    public boolean retryable() {
        return retryable;
    }

    /** Redacted text of the provider or its SDK about the failure; empty when there is none. Never persist it. */
    public String detail() {
        return detail;
    }
}
