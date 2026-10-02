package dev.apidocs.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProjectConfigLoaderTest {

    @TempDir
    Path dir;

    private Path file(String content) throws IOException {
        return Files.writeString(dir.resolve(".apidocs.yml"), content);
    }

    @Test
    void readsEverySetting() throws IOException {
        ProjectConfigFile config = new ProjectConfigLoader().load(file("""
                language: en
                context:
                  description: "B2C orders API"
                  audience: "Front-end developers"
                  glossary:
                    SKU: "Stock keeping unit"
                  instructions: "Highlight stock rules."
                llm:
                  provider: gemini
                  model: gemini-3.8-flash
                  baseUrl: http://proxy/v1
                  apiKeyEnv: MY_KEY
                exclude:
                  - "**/legacy/**"
                """)).orElseThrow();

        assertThat(config.language()).isEqualTo("en");
        assertThat(config.context()).isEqualTo(new ContextConfig("B2C orders API", "Front-end developers",
                java.util.Map.of("SKU", "Stock keeping unit"), "Highlight stock rules."));
        assertThat(config.provider()).isEqualTo("gemini");
        assertThat(config.model()).isEqualTo("gemini-3.8-flash");
        assertThat(config.baseUrl()).isEqualTo("http://proxy/v1");
        assertThat(config.apiKeyEnv()).isEqualTo("MY_KEY");
        assertThat(config.exclude()).containsExactly("**/legacy/**");
    }

    @Test
    void rejectsSecretsAnywhere() throws IOException {
        Path nested = file("llm:\n  provider: gemini\n  apiKey: abc\n");
        assertThatThrownBy(() -> new ProjectConfigLoader().load(nested))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("llm.apiKey")
                .hasMessageContaining("environment variables");
        Path snake = file("api_key: abc\n");
        assertThatThrownBy(() -> new ProjectConfigLoader().load(snake)).isInstanceOf(ConfigException.class);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            'llm: {provider: openai, apiKeyEnv: sk-proj-AbCdEfGhIjKlMnOpQrSt}'                 | llm.apiKeyEnv          | sk-proj-AbCdEfGhIjKlMnOpQrSt
            'context: {instructions: "Test with AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r."}'   | context.instructions   | AIzaSyB1c2d3e4f5g6h7i8j9k0l1m2n3o4p5q6r
            'context: {glossary: {Access: ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789}}'          | context.glossary.Access | ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789
            'exclude: ["**/legacy/**", AKIAIOSFODNN7EXAMPLE]'                                   | exclude[1]             | AKIAIOSFODNN7EXAMPLE
            'notes: {deep: [{x: xoxb-1234567890-abcdefghij}]}'                                  | notes.deep[0].x        | xoxb-1234567890-abcdefghij
            'llm: {provider: custom, apiKeyEnv: gsk_AbCdEfGhIjKlMnOpQrStUv01}'                 | llm.apiKeyEnv          | gsk_AbCdEfGhIjKlMnOpQrStUv01
            'context: {description: "Model token hf_AbCdEfGhIjKlMnOpQrStUv01"}'                | context.description    | hf_AbCdEfGhIjKlMnOpQrStUv01
            'context: {glossary: {Grok: xai-AbCdEfGhIjKlMnOpQrStUv01}}'                        | context.glossary.Grok  | xai-AbCdEfGhIjKlMnOpQrStUv01
            'exclude: [pplx-AbCdEfGhIjKlMnOpQrStUv01]'                                          | exclude[0]             | pplx-AbCdEfGhIjKlMnOpQrStUv01
            """)
    void rejectsSecretValuesAnywhereWithoutEchoingThem(String yaml, String path, String secret) throws IOException {
        Path leaky = file(yaml + "\n");

        assertThatThrownBy(() -> new ProjectConfigLoader().load(leaky))
                .isInstanceOf(ConfigException.class)
                .hasMessage(".apidocs.yml " + path
                        + " looks like a secret (API key/token); keep secrets in environment variables.")
                .hasMessageNotContaining(secret);
    }

    @Test
    void rejectsKeysThatLookLikeSecretsWithoutEchoingThem() throws IOException {
        Path leaky = file("context:\n  glossary:\n    sk-proj-AbCdEfGhIjKlMnOpQrSt: our key\n");

        assertThatThrownBy(() -> new ProjectConfigLoader().load(leaky))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("context.glossary")
                .hasMessageContaining("looks like a secret")
                .hasMessageNotContaining("sk-proj-");
    }

    @Test
    void acceptsOrdinaryTextThatOnlyResemblesASecretFormat() throws IOException {
        ProjectConfigFile config = new ProjectConfigLoader().load(file("""
                context:
                  instructions: "Follow the risk-assessment-guidelines; tokens are described in docs/sk-notes.md."
                  glossary:
                    Bearer: "Whoever holds a bearer token"
                exclude:
                  - "**/task-management-service/**"
                """)).orElseThrow();

        assertThat(config.context().instructions()).startsWith("Follow the risk-assessment-guidelines");
        assertThat(config.exclude()).containsExactly("**/task-management-service/**");
    }

    @Test
    void treatsYamlNullsAndBlankValuesAsNotSet() throws IOException {
        ProjectConfigFile config = new ProjectConfigLoader().load(file("""
                language:
                context:
                  description: ~
                  audience: ""
                  glossary:
                  instructions: null
                llm:
                  provider: ~
                  model:
                  baseUrl: ""
                  apiKeyEnv: Null
                exclude:
                """)).orElseThrow();

        assertThat(config).isEqualTo(ProjectConfigFile.EMPTY);
        assertThat(new ProjectConfigLoader().load(file("llm:\ncontext:\n")).orElseThrow())
                .isEqualTo(ProjectConfigFile.EMPTY);
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            exclude: "**/legacy/**"                | exclude must be a list of glob strings
            exclude: {a: b}                        | exclude must be a list of glob strings
            exclude: [{a: b}]                      | exclude must be a list of glob strings
            exclude: [[a]]                         | exclude must be a list of glob strings
            exclude: [~]                           | exclude must be a list of glob strings
            exclude: ["  "]                        | exclude must be a list of glob strings
            llm: openai                            | llm must be a mapping
            llm: [openai]                          | llm must be a mapping
            context: some text                     | context must be a mapping
            'context: {glossary: [a]}'             | context.glossary must be a mapping
            'context: {glossary: text}'            | context.glossary must be a mapping
            'context: {glossary: {SKU: [a]}}'      | context.glossary.SKU must be a text value
            'context: {description: [a]}'          | context.description must be a text value
            'context: {instructions: {a: b}}'      | context.instructions must be a text value
            'llm: {model: {a: b}}'                 | llm.model must be a text value
            'llm: {baseUrl: [x]}'                  | llm.baseUrl must be a text value
            language: [en]                         | language must be a text value
            """)
    void rejectsWrongShapedValuesInsteadOfIgnoringThem(String yaml, String expectedMessage) throws IOException {
        Path wrong = file(yaml + "\n");

        assertThatThrownBy(() -> new ProjectConfigLoader().load(wrong))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining(".apidocs.yml")
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void handlesMissingEmptyAndInvalidFiles() throws IOException {
        assertThat(new ProjectConfigLoader().load(dir.resolve("absent.yml"))).isEmpty();
        assertThat(new ProjectConfigLoader().load(file(""))).contains(ProjectConfigFile.EMPTY);
        Path broken = file("a: [");
        assertThatThrownBy(() -> new ProjectConfigLoader().load(broken)).isInstanceOf(ConfigException.class);
        Path list = file("- a\n- b\n");
        assertThatThrownBy(() -> new ProjectConfigLoader().load(list)).isInstanceOf(ConfigException.class);
    }
}
