package dev.apidocs.core.config;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.ConfigException;
import dev.apidocs.core.ai.LlmSettings;
import dev.apidocs.core.ai.ProviderPreset;
import dev.apidocs.core.source.SourceLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/** Precedence: flags, then environment variables, then {@code .apidocs.yml}, then defaults. */
public final class ConfigResolver {

    static final int DEFAULT_MAX_OUTPUT_TOKENS = 16_000;
    static final String DEFAULT_EFFORT = "high";
    private static final Pattern ALLOWED_KEY_VARIABLES = Pattern.compile("^APIDOCS_[A-Z0-9_]+$");

    private final ProjectConfigLoader loader = new ProjectConfigLoader();

    public GeneratorConfig resolve(ConfigOverrides overrides, Map<String, String> environment) {
        if (overrides.projectDir() == null) {
            throw new ConfigException("Missing project directory.");
        }
        Path projectDir = overrides.projectDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(projectDir)) {
            throw new AnalysisException("Project directory not found: " + projectDir);
        }
        Path configFile = overrides.configFile() != null
                ? overrides.configFile().toAbsolutePath().normalize()
                : projectDir.resolve(ProjectConfigLoader.FILE_NAME);
        if (overrides.configFile() != null && !Files.isRegularFile(configFile)) {
            throw new ConfigException("Config file not found: " + configFile);
        }
        ProjectConfigFile file = loader.load(configFile).orElse(ProjectConfigFile.EMPTY);

        ProviderPreset preset = ProviderPreset.fromId(
                first(overrides.provider(), environment.get("APIDOCS_PROVIDER"), file.provider(), "dry-run"));
        // The file's model, base URL and API-key variable belong to the provider the file declares (if any).
        // They must not leak into a run that selected another provider by flag or environment: that would send
        // that provider's credentials to an endpoint configured for a different one.
        boolean fileApplies = file.provider().isBlank() || ProviderPreset.fromId(file.provider()) == preset;
        // The analyzed repository is untrusted and owns the file, so it must not decide where credentials go or
        // which secret is read: it may only pick the endpoint of providers without an official one, and only name
        // dedicated APIDOCS_* variables. Flags are the user's own and stay unrestricted.
        String fileModel = fileApplies ? file.model() : "";
        String fileBaseUrl = fileApplies && fileMayChooseEndpoint(preset) ? file.baseUrl() : "";
        String fileApiKeyEnv = fileApplies ? file.apiKeyEnv() : "";
        if (isBlank(overrides.apiKeyEnv())) {
            requireAllowedKeyVariable(fileApiKeyEnv, preset);
        }

        String model = first(overrides.model(), environment.get("APIDOCS_MODEL"), fileModel, preset.defaultModel());
        String baseUrl = first(overrides.baseUrl(), fileBaseUrl, preset.baseUrl());
        String apiKeyEnv = first(overrides.apiKeyEnv(), fileApiKeyEnv, preset.apiKeyEnv());
        String language = first(overrides.language(), environment.get("APIDOCS_LANGUAGE"), file.language(), "pt-BR");

        Path outputDir = (overrides.outputDir() != null ? overrides.outputDir() : Path.of("api-docs"))
                .toAbsolutePath().normalize();
        if (outputDir.equals(projectDir) || projectDir.startsWith(outputDir)) {
            throw new ConfigException("The output directory must not be the project directory or one of its parents: "
                    + outputDir);
        }
        Path cacheDir = (overrides.cacheDir() != null ? overrides.cacheDir() : Path.of(".apidocs-cache"))
                .toAbsolutePath().normalize();
        return new GeneratorConfig(projectDir, outputDir, cacheDir, !overrides.noCache(), language,
                new LlmSettings(preset, model, baseUrl, apiKeyEnv, DEFAULT_EFFORT), file.context(), file.exclude(),
                overrides.strict(), SourceLimits.DEFAULT, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    /** Self-hosted or user-chosen endpoints; gemini, openai and anthropic always use their official one. */
    private static boolean fileMayChooseEndpoint(ProviderPreset preset) {
        return preset == ProviderPreset.CUSTOM || preset == ProviderPreset.OLLAMA;
    }

    private static void requireAllowedKeyVariable(String name, ProviderPreset preset) {
        if (name.isBlank() || name.equals(preset.apiKeyEnv()) || ALLOWED_KEY_VARIABLES.matcher(name).matches()) {
            return;
        }
        throw new ConfigException(ProjectConfigLoader.FILE_NAME + " may only name API key variables starting with "
                + "APIDOCS_ (got " + name + "); pass --api-key-env to use another variable");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }
}
