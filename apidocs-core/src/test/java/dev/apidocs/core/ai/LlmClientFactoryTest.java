package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.ConfigException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmClientFactoryTest {

    @TempDir
    Path cache;

    private final LlmClientFactory factory = new LlmClientFactory();

    private static LlmSettings settings(ProviderPreset preset) {
        return new LlmSettings(preset, preset.defaultModel(), preset.baseUrl(), preset.apiKeyEnv(), "high");
    }

    @Test
    void dryRunIsNeverDecorated() {
        assertThat(factory.create(settings(ProviderPreset.DRY_RUN), Map.of(), cache, true)).isInstanceOf(DryRunLlmClient.class);
    }

    @Test
    void wrapsRemoteProvidersWithRateLimitAndCache() {
        LlmClient cached = factory.create(settings(ProviderPreset.GEMINI), Map.of("GEMINI_API_KEY", "k"), cache, true);
        LlmClient uncached = factory.create(settings(ProviderPreset.GEMINI), Map.of("GEMINI_API_KEY", "k"), cache, false);

        assertThat(cached).isInstanceOf(CachingLlmClient.class);
        assertThat(cached.id()).isEqualTo("generativelanguage.googleapis.com:gemini-3.8-flash");
        assertThat(uncached).isInstanceOf(RateLimitedLlmClient.class);
    }

    @Test
    void createsEachAdapter() {
        assertThat(factory.create(settings(ProviderPreset.OLLAMA), Map.of(), cache, false).id())
                .isEqualTo("localhost:qwen3:8b");
        assertThat(factory.create(settings(ProviderPreset.ANTHROPIC), Map.of("ANTHROPIC_API_KEY", "k"), cache, false).id())
                .isEqualTo("anthropic:claude-opus-5-5");
        assertThat(factory.create(settings(ProviderPreset.OPENAI), Map.of("OPENAI_API_KEY", "k"), cache, false))
                .isInstanceOf(OpenAiCompatibleLlmClient.class);
    }

    @Test
    void explainsMissingKeysAndSettings() {
        assertThatThrownBy(() -> factory.create(settings(ProviderPreset.GEMINI), Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("GEMINI_API_KEY")
                .hasMessageContaining("--provider dry-run");
        LlmSettings custom = new LlmSettings(ProviderPreset.CUSTOM, "m", "", "", "high");
        assertThatThrownBy(() -> factory.create(custom, Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--base-url");
        LlmSettings noModel = new LlmSettings(ProviderPreset.CUSTOM, "", "http://x/v1", "", "high");
        assertThatThrownBy(() -> factory.create(noModel, Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--model");
    }

    @Test
    void reportsUnusableKeysWithoutEchoingThem() {
        for (ProviderPreset preset : new ProviderPreset[] {ProviderPreset.GEMINI, ProviderPreset.ANTHROPIC}) {
            Map<String, String> environment = Map.of(preset.apiKeyEnv(), "sk-x\n");

            assertThatThrownBy(() -> factory.create(settings(preset), environment, cache, true))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining(preset.id())
                    .hasMessageNotContaining("sk-x")
                    .cause().isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("sk-x");
        }
    }

    @Test
    void neverEchoesAnApiKeyPastedAsTheVariableName() {
        String pasted = "sk-proj-AbC123/xyz+9";
        LlmSettings openai = new LlmSettings(ProviderPreset.OPENAI, "gpt-x", ProviderPreset.OPENAI.baseUrl(), pasted,
                "high");

        assertThatThrownBy(() -> factory.create(openai, Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--api-key-env")
                .hasMessageContaining("not a valid environment variable name")
                .hasMessageNotContaining(pasted);
        LlmSettings named = new LlmSettings(ProviderPreset.OPENAI, "gpt-x", ProviderPreset.OPENAI.baseUrl(),
                "MY_OPENAI_KEY", "high");
        assertThatThrownBy(() -> factory.create(named, Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("MY_OPENAI_KEY");
    }

    @Test
    void neverEchoesAnApiKeyPastedAsTheVariableNameEvenWhenItIsAValidName() {
        for (String pasted : List.of("AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r7s", "abcd1234efgh5678ijkl9012mnop")) {
            LlmSettings gemini = new LlmSettings(ProviderPreset.GEMINI, "gemini-3.8-flash",
                    ProviderPreset.GEMINI.baseUrl(), pasted, "high");

            assertThatThrownBy(() -> factory.create(gemini, Map.of(), cache, true))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("--api-key-env")
                    .hasMessageContaining("not shown")
                    .hasMessageNotContaining(pasted);
        }
    }

    @Test
    void reportsMalformedBaseUrls() {
        LlmSettings custom = new LlmSettings(ProviderPreset.CUSTOM, "m", "http://exa mple/v1", "", "high");

        assertThatThrownBy(() -> factory.create(custom, Map.of(), cache, true))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("custom");
    }

    @Test
    void resolvesPresetsByIdCaseInsensitively() {
        assertThat(ProviderPreset.fromId("GEMINI")).isEqualTo(ProviderPreset.GEMINI);
        assertThat(ProviderPreset.fromId("dry-run")).isEqualTo(ProviderPreset.DRY_RUN);
        assertThatThrownBy(() -> ProviderPreset.fromId("bard"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("gemini, ollama, openai, anthropic, custom, dry-run");
    }
}
