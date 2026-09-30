package dev.apidocs.core.ai;

/** Port to any Large Language Model. Implementations must be safe to call sequentially from one thread. */
public interface LlmClient {

    /** Provider and model, e.g. {@code gemini:gemini-3.8-flash}; used in cache keys and reports. */
    String id();

    LlmResponse complete(LlmRequest request);
}
