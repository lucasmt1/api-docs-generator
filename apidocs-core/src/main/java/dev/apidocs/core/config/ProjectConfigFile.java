package dev.apidocs.core.config;

import java.util.List;

/** Contents of {@code .apidocs.yml}. Empty strings mean "not set". */
public record ProjectConfigFile(
        String language,
        ContextConfig context,
        String provider,
        String model,
        String baseUrl,
        String apiKeyEnv,
        List<String> exclude) {

    public static final ProjectConfigFile EMPTY =
            new ProjectConfigFile("", ContextConfig.EMPTY, "", "", "", "", List.of());

    public ProjectConfigFile {
        language = language == null ? "" : language;
        context = context == null ? ContextConfig.EMPTY : context;
        provider = provider == null ? "" : provider;
        model = model == null ? "" : model;
        baseUrl = baseUrl == null ? "" : baseUrl;
        apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv;
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}
