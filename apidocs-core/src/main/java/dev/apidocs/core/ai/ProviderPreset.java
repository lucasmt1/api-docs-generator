package dev.apidocs.core.ai;

import dev.apidocs.core.ConfigException;
import dev.apidocs.core.ai.OpenAiCompatibleLlmClient.JsonMode;
import dev.apidocs.core.ai.OpenAiCompatibleLlmClient.Options;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Known LLM providers. Empty strings mean "not applicable". */
public enum ProviderPreset {
    GEMINI("gemini", Adapter.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai/",
            "GEMINI_API_KEY", "gemini-3.8-flash", new Options(JsonMode.JSON_SCHEMA, true, "max_tokens"), 10, 200_000),
    OLLAMA("ollama", Adapter.OPENAI_COMPATIBLE, "http://localhost:11434/v1/", "", "qwen3:8b",
            new Options(JsonMode.JSON_OBJECT, true, "max_tokens"), 0, 24_000),
    OPENAI("openai", Adapter.OPENAI_COMPATIBLE, "https://api.openai.com/v1/", "OPENAI_API_KEY", "gpt-5-mini",
            new Options(JsonMode.JSON_SCHEMA, false, "max_completion_tokens"), 0, 200_000),
    ANTHROPIC("anthropic", Adapter.ANTHROPIC, "", "ANTHROPIC_API_KEY", AnthropicLlmClient.DEFAULT_MODEL, null, 0, 400_000),
    CUSTOM("custom", Adapter.OPENAI_COMPATIBLE, "", "", "", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"), 0, 32_000),
    DRY_RUN("dry-run", Adapter.DRY_RUN, "", "", "dry-run", null, 0, 200_000);

    public enum Adapter { OPENAI_COMPATIBLE, ANTHROPIC, DRY_RUN }

    private final String id;
    private final Adapter adapter;
    private final String baseUrl;
    private final String apiKeyEnv;
    private final String defaultModel;
    private final Options options;
    private final int requestsPerMinute;
    private final int maxInputTokens;

    ProviderPreset(String id, Adapter adapter, String baseUrl, String apiKeyEnv, String defaultModel, Options options,
            int requestsPerMinute, int maxInputTokens) {
        this.id = id;
        this.adapter = adapter;
        this.baseUrl = baseUrl;
        this.apiKeyEnv = apiKeyEnv;
        this.defaultModel = defaultModel;
        this.options = options;
        this.requestsPerMinute = requestsPerMinute;
        this.maxInputTokens = maxInputTokens;
    }

    public static ProviderPreset fromId(String id) {
        return Arrays.stream(values())
                .filter(preset -> preset.id.equalsIgnoreCase(id.strip()))
                .findFirst()
                .orElseThrow(() -> new ConfigException("Unknown provider '" + id + "'. Use one of: "
                        + Arrays.stream(values()).map(ProviderPreset::id).collect(Collectors.joining(", "))));
    }

    public String id() {
        return id;
    }

    public Adapter adapter() {
        return adapter;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKeyEnv() {
        return apiKeyEnv;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public Options options() {
        return options;
    }

    public int requestsPerMinute() {
        return requestsPerMinute;
    }

    public int maxInputTokens() {
        return maxInputTokens;
    }
}
