package dev.apidocs.core.ai;

import dev.apidocs.core.ConfigException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.regex.Pattern;

/** Builds the configured adapter and its decorators: adapter, then rate limit, then cache. */
public final class LlmClientFactory {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * API keys come only from {@code environment}. Nothing thrown from here contains a key: an unusable key or base
     * URL surfaces as a {@link ConfigException} carrying the adapter's own (key-free) message.
     */
    public LlmClient create(LlmSettings settings, Map<String, String> environment, Path cacheDir, boolean cacheEnabled) {
        ProviderPreset preset = settings.provider();
        if (preset.adapter() == ProviderPreset.Adapter.DRY_RUN) {
            return new DryRunLlmClient();
        }
        if (settings.model() == null || settings.model().isBlank()) {
            throw new ConfigException("Provider '" + preset.id() + "' requires --model.");
        }
        LlmClient client;
        try {
            client = switch (preset.adapter()) {
                case ANTHROPIC -> AnthropicLlmClient.create(requiredKey(settings, environment),
                        blankToNull(settings.baseUrl()), settings.model(), settings.effort(), 4);
                case OPENAI_COMPATIBLE -> OpenAiCompatibleLlmClient.create(requiredBaseUrl(settings),
                        optionalKey(settings, environment), settings.model(), preset.options());
                case DRY_RUN -> throw new IllegalStateException("handled above");
            };
        } catch (IllegalArgumentException e) {
            // The adapters reject a key that cannot be an HTTP header value, or a malformed base URL, with messages
            // that never include the key.
            throw new ConfigException("Invalid configuration for provider '" + preset.id() + "': " + e.getMessage(), e);
        }
        if (preset.requestsPerMinute() > 0) {
            client = new RateLimitedLlmClient(client, preset.requestsPerMinute(), Clock.systemUTC(), Sleeper.REAL);
        }
        return cacheEnabled ? new CachingLlmClient(client, cacheDir) : client;
    }

    private static String requiredKey(LlmSettings settings, Map<String, String> environment) {
        String variable = settings.apiKeyEnv() == null ? "" : settings.apiKeyEnv();
        String value = variable.isBlank() ? null : environment.get(variable);
        if (value == null || value.isBlank()) {
            if (!variable.isBlank() && !VARIABLE_NAME.matcher(variable).matches()) {
                // never echo it: users sometimes paste the key itself instead of the variable name
                throw new ConfigException("Missing API key for provider '" + settings.provider().id()
                        + "': the --api-key-env value is not a valid environment variable name (not shown, in case"
                        + " it is the key itself); pass the name of the variable that holds the key.");
            }
            throw new ConfigException("Missing API key for provider '" + settings.provider().id() + "': set the "
                    + (variable.isBlank() ? "API key" : variable) + " environment variable, "
                    + "or use --provider dry-run to generate the documentation without an LLM.");
        }
        return value;
    }

    private static String optionalKey(LlmSettings settings, Map<String, String> environment) {
        return settings.apiKeyEnv() == null || settings.apiKeyEnv().isBlank() ? null : requiredKey(settings, environment);
    }

    private static String requiredBaseUrl(LlmSettings settings) {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()) {
            throw new ConfigException("Provider '" + settings.provider().id() + "' requires --base-url.");
        }
        return settings.baseUrl();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
