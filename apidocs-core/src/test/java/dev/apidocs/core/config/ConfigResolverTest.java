package dev.apidocs.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.ConfigException;
import dev.apidocs.core.ai.ProviderPreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigResolverTest {

    @TempDir
    Path dir;

    private final ConfigResolver resolver = new ConfigResolver();

    private static ConfigOverrides overrides(Path project, Path output, String provider, String language) {
        return new ConfigOverrides(project, output, null, provider, null, null, null, language, null, false, false);
    }

    @Test
    void appliesDefaults() {
        GeneratorConfig config = resolver.resolve(overrides(dir, null, null, null), Map.of());

        assertThat(config.projectDir()).isEqualTo(dir.toAbsolutePath().normalize());
        assertThat(config.outputDir()).isEqualTo(Path.of("api-docs").toAbsolutePath().normalize());
        assertThat(config.llm().provider()).isEqualTo(ProviderPreset.DRY_RUN);
        assertThat(config.llm().model()).isEqualTo("dry-run");
        assertThat(config.llm().effort()).isEqualTo("high");
        assertThat(config.language()).isEqualTo("pt-BR");
        assertThat(config.cacheEnabled()).isTrue();
        // per user, never inside the analyzed project or the current directory: cached answers quote its code
        assertThat(config.cacheDir()).isAbsolute()
                .isEqualTo(Path.of(System.getProperty("user.home"), ".cache", "apidocs").toAbsolutePath().normalize());
        assertThat(config.strict()).isFalse();
        assertThat(config.maxOutputTokens()).isEqualTo(16_000);
        assertThat(config.maxInputTokens()).isEqualTo(ProviderPreset.DRY_RUN.maxInputTokens());
    }

    @Test
    void flagsBeatEnvironmentWhichBeatsTheFile() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), """
                language: en
                llm:
                  provider: ollama
                context:
                  description: From file
                exclude: ["**/gen/**"]
                """);

        GeneratorConfig fromFile = resolver.resolve(overrides(dir, null, null, null), Map.of());
        GeneratorConfig fromEnv = resolver.resolve(overrides(dir, null, null, null),
                Map.of("APIDOCS_PROVIDER", "gemini", "APIDOCS_LANGUAGE", "es"));
        GeneratorConfig fromFlags = resolver.resolve(overrides(dir, null, "openai", "fr"),
                Map.of("APIDOCS_PROVIDER", "gemini", "APIDOCS_LANGUAGE", "es"));

        assertThat(fromFile.llm().provider()).isEqualTo(ProviderPreset.OLLAMA);
        assertThat(fromFile.llm().model()).isEqualTo("qwen3:8b");
        assertThat(fromFile.llm().baseUrl()).isEqualTo("http://localhost:11434/v1/");
        assertThat(fromFile.language()).isEqualTo("en");
        assertThat(fromFile.context().description()).isEqualTo("From file");
        assertThat(fromFile.exclude()).containsExactly("**/gen/**");
        assertThat(fromEnv.llm().provider()).isEqualTo(ProviderPreset.GEMINI);
        assertThat(fromEnv.language()).isEqualTo("es");
        assertThat(fromFlags.llm().provider()).isEqualTo(ProviderPreset.OPENAI);
        assertThat(fromFlags.llm().apiKeyEnv()).isEqualTo("OPENAI_API_KEY");
        assertThat(fromFlags.language()).isEqualTo("fr");
    }

    @Test
    void providerSpecificFileValuesApplyOnlyToTheProviderTheFileDeclares() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), """
                llm:
                  provider: custom
                  baseUrl: http://proxy.local/v1/
                  model: m1
                  apiKeyEnv: APIDOCS_MY_KEY
                """);

        GeneratorConfig fromFile = resolver.resolve(overrides(dir, null, null, null), Map.of());
        GeneratorConfig otherProvider = resolver.resolve(overrides(dir, null, "openai", null), Map.of());
        GeneratorConfig otherProviderByEnv = resolver.resolve(overrides(dir, null, null, null),
                Map.of("APIDOCS_PROVIDER", "openai"));

        assertThat(fromFile.llm().provider()).isEqualTo(ProviderPreset.CUSTOM);
        assertThat(fromFile.llm().baseUrl()).isEqualTo("http://proxy.local/v1/");
        assertThat(fromFile.llm().model()).isEqualTo("m1");
        assertThat(fromFile.llm().apiKeyEnv()).isEqualTo("APIDOCS_MY_KEY");
        for (GeneratorConfig config : List.of(otherProvider, otherProviderByEnv)) {
            assertThat(config.llm().provider()).isEqualTo(ProviderPreset.OPENAI);
            assertThat(config.llm().baseUrl()).isEqualTo(ProviderPreset.OPENAI.baseUrl());
            assertThat(config.llm().model()).isEqualTo(ProviderPreset.OPENAI.defaultModel());
            assertThat(config.llm().apiKeyEnv()).isEqualTo("OPENAI_API_KEY");
        }
    }

    @Test
    void providerSpecificFileValuesApplyWhenTheFileDeclaresNoProvider() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), """
                llm:
                  model: tuned
                  baseUrl: http://proxy.local/v1/
                  apiKeyEnv: APIDOCS_MY_KEY
                """);

        GeneratorConfig defaultProvider = resolver.resolve(overrides(dir, null, null, null), Map.of());
        GeneratorConfig flagProvider = resolver.resolve(overrides(dir, null, "openai", null), Map.of());
        GeneratorConfig customProvider = resolver.resolve(overrides(dir, null, "custom", null), Map.of());

        assertThat(defaultProvider.llm().provider()).isEqualTo(ProviderPreset.DRY_RUN);
        assertThat(defaultProvider.llm().model()).isEqualTo("tuned");
        assertThat(flagProvider.llm().provider()).isEqualTo(ProviderPreset.OPENAI);
        assertThat(flagProvider.llm().model()).isEqualTo("tuned");
        assertThat(flagProvider.llm().baseUrl()).isEqualTo(ProviderPreset.OPENAI.baseUrl());
        assertThat(flagProvider.llm().apiKeyEnv()).isEqualTo("APIDOCS_MY_KEY");
        assertThat(customProvider.llm().baseUrl()).isEqualTo("http://proxy.local/v1/");
    }

    @Test
    void theFileCannotRedirectAProviderWithAnOfficialEndpoint() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), """
                llm:
                  provider: openai
                  baseUrl: http://evil.local/v1/
                """);
        ConfigOverrides urlFlag = new ConfigOverrides(dir, null, null, null, null, "http://my-gateway/v1/", null, null,
                null, false, false);

        GeneratorConfig fromFile = resolver.resolve(overrides(dir, null, null, null), Map.of());
        GeneratorConfig fromFlag = resolver.resolve(urlFlag, Map.of());

        assertThat(fromFile.llm().provider()).isEqualTo(ProviderPreset.OPENAI);
        assertThat(fromFile.llm().baseUrl()).isEqualTo(ProviderPreset.OPENAI.baseUrl());
        assertThat(fromFlag.llm().baseUrl()).isEqualTo("http://my-gateway/v1/");
        for (String provider : List.of("gemini", "openai", "anthropic")) {
            Files.writeString(dir.resolve(".apidocs.yml"),
                    "llm:\n  provider: " + provider + "\n  baseUrl: http://evil.local/v1/\n");
            assertThat(resolver.resolve(overrides(dir, null, null, null), Map.of()).llm().baseUrl())
                    .isEqualTo(ProviderPreset.fromId(provider).baseUrl());
        }
    }

    @Test
    void theFileMayChooseTheEndpointOfSelfHostedProviders() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"),
                "llm:\n  provider: ollama\n  baseUrl: http://gpu-box:11434/v1/\n");

        assertThat(resolver.resolve(overrides(dir, null, null, null), Map.of()).llm().baseUrl())
                .isEqualTo("http://gpu-box:11434/v1/");
    }

    @Test
    void theFileMayOnlyNameApidocsPrefixedOrDefaultKeyVariables() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), """
                llm:
                  provider: custom
                  baseUrl: http://proxy.local/v1/
                  apiKeyEnv: AWS_SECRET_ACCESS_KEY
                """);
        ConfigOverrides keyFlag = new ConfigOverrides(dir, null, null, null, null, null, "MY_OWN_KEY", null, null,
                false, false);

        assertThatThrownBy(() -> resolver.resolve(overrides(dir, null, null, null), Map.of()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("APIDOCS_")
                .hasMessageContaining("AWS_SECRET_ACCESS_KEY")
                .hasMessageContaining("--api-key-env");
        // a flag is unrestricted and replaces the file's variable
        assertThat(resolver.resolve(keyFlag, Map.of()).llm().apiKeyEnv()).isEqualTo("MY_OWN_KEY");
        // the file's variable is not used when another provider is selected, so it is not an error either
        assertThat(resolver.resolve(overrides(dir, null, "openai", null), Map.of()).llm().apiKeyEnv())
                .isEqualTo("OPENAI_API_KEY");

        Files.writeString(dir.resolve(".apidocs.yml"), """
                llm:
                  provider: custom
                  baseUrl: http://proxy.local/v1/
                  apiKeyEnv: APIDOCS_PROXY_KEY
                """);
        GeneratorConfig accepted = resolver.resolve(overrides(dir, null, null, null), Map.of());
        assertThat(accepted.llm().baseUrl()).isEqualTo("http://proxy.local/v1/");
        assertThat(accepted.llm().apiKeyEnv()).isEqualTo("APIDOCS_PROXY_KEY");

        Files.writeString(dir.resolve(".apidocs.yml"), "llm:\n  provider: openai\n  apiKeyEnv: OPENAI_API_KEY\n");
        assertThat(resolver.resolve(overrides(dir, null, null, null), Map.of()).llm().apiKeyEnv())
                .isEqualTo("OPENAI_API_KEY");

        Files.writeString(dir.resolve(".apidocs.yml"), "llm:\n  provider: openai\n  apiKeyEnv: apidocs_lower\n");
        assertThatThrownBy(() -> resolver.resolve(overrides(dir, null, null, null), Map.of()))
                .isInstanceOf(ConfigException.class).hasMessageContaining("apidocs_lower");
        Files.writeString(dir.resolve(".apidocs.yml"), "llm:\n  provider: openai\n  apiKeyEnv: GEMINI_API_KEY\n");
        assertThatThrownBy(() -> resolver.resolve(overrides(dir, null, null, null), Map.of()))
                .isInstanceOf(ConfigException.class).hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void neverEchoesAKeyPastedAsTheFilesKeyVariable() throws IOException {
        for (String pasted : List.of("abcd1234efgh5678ijkl9012mnop", "my key value", "key=abc")) {
            Files.writeString(dir.resolve(".apidocs.yml"), "llm:\n  provider: openai\n  apiKeyEnv: \"" + pasted + "\"\n");

            assertThatThrownBy(() -> resolver.resolve(overrides(dir, null, null, null), Map.of()))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining(".apidocs.yml llm.apiKeyEnv must be the NAME of an environment variable")
                    .hasMessageContaining("APIDOCS_")
                    .hasMessageContaining("--api-key-env")
                    .hasMessageNotContaining(pasted);
        }
    }

    @Test
    void flagsAndEnvironmentModelStillBeatTheFileForTheSameProvider() throws IOException {
        Files.writeString(dir.resolve(".apidocs.yml"), "llm:\n  provider: openai\n  model: from-file\n");
        ConfigOverrides modelFlag = new ConfigOverrides(dir, null, null, null, "from-flag", null, null, null, null,
                false, false);

        assertThat(resolver.resolve(modelFlag, Map.of("APIDOCS_MODEL", "from-env")).llm().model())
                .isEqualTo("from-flag");
        assertThat(resolver.resolve(overrides(dir, null, null, null), Map.of("APIDOCS_MODEL", "from-env"))
                .llm().model()).isEqualTo("from-env");
        assertThat(resolver.resolve(overrides(dir, null, null, null), Map.of()).llm().model())
                .isEqualTo("from-file");
    }

    @Test
    void refusesDangerousOrMissingPaths() {
        assertThatThrownBy(() -> resolver.resolve(overrides(dir.resolve("nope"), null, null, null), Map.of()))
                .isInstanceOf(AnalysisException.class).hasMessageContaining("Project directory not found");
        assertThatThrownBy(() -> resolver.resolve(overrides(dir, dir, null, null), Map.of()))
                .isInstanceOf(ConfigException.class).hasMessageContaining("output directory");
        assertThatThrownBy(() -> resolver.resolve(overrides(dir, dir.getParent(), null, null), Map.of()))
                .isInstanceOf(ConfigException.class);
        ConfigOverrides missingConfig = new ConfigOverrides(dir, null, dir.resolve("missing.yml"), null, null, null,
                null, null, null, false, false);
        assertThatThrownBy(() -> resolver.resolve(missingConfig, Map.of()))
                .isInstanceOf(ConfigException.class).hasMessageContaining("Config file not found");
    }

    @Test
    void passesCacheAndStrictFlags() {
        ConfigOverrides flags = new ConfigOverrides(dir, dir.resolve("out"), null, null, null, null, null, null,
                dir.resolve("cache"), true, true);

        GeneratorConfig config = resolver.resolve(flags, Map.of());

        assertThat(config.cacheEnabled()).isFalse();
        assertThat(config.strict()).isTrue();
        assertThat(config.cacheDir()).isEqualTo(dir.resolve("cache").toAbsolutePath().normalize());
        assertThat(config.exclude()).isEqualTo(List.of());
    }
}
