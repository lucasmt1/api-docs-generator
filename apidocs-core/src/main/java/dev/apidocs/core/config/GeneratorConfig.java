package dev.apidocs.core.config;

import dev.apidocs.core.ai.LlmSettings;
import dev.apidocs.core.source.SourceLimits;
import java.nio.file.Path;
import java.util.List;

/** Fully resolved configuration of one run. */
public record GeneratorConfig(
        Path projectDir,
        Path outputDir,
        Path cacheDir,
        boolean cacheEnabled,
        String language,
        LlmSettings llm,
        ContextConfig context,
        List<String> exclude,
        boolean strict,
        SourceLimits limits,
        int maxOutputTokens) {

    public GeneratorConfig {
        exclude = List.copyOf(exclude);
    }

    public int maxInputTokens() {
        return llm.provider().maxInputTokens();
    }
}
