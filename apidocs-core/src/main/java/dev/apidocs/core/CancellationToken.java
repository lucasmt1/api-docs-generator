package dev.apidocs.core;

/** Cooperative cancellation, checked between pipeline stages and before every LLM call. */
public final class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() {
        if (cancelled) {
            throw new CancelledException("Generation cancelled");
        }
    }
}
