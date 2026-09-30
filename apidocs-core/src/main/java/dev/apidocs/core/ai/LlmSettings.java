package dev.apidocs.core.ai;

/** Resolved LLM configuration. Empty strings mean "not set". */
public record LlmSettings(ProviderPreset provider, String model, String baseUrl, String apiKeyEnv, String effort) {
}
