package dev.apidocs.core.config;

import java.nio.file.Path;

/** Values given on the command line (or by another front-end); {@code null} means "not given". */
public record ConfigOverrides(
        Path projectDir,
        Path outputDir,
        Path configFile,
        String provider,
        String model,
        String baseUrl,
        String apiKeyEnv,
        String language,
        Path cacheDir,
        boolean noCache,
        boolean strict) {
}
