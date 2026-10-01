package dev.apidocs.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.config.ConfigOverrides;
import dev.apidocs.core.config.ConfigResolver;
import dev.apidocs.core.config.GeneratorConfig;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.FakeLlmClient;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Full pipeline on examples/sample-api with scripted LLM answers, compared with reviewed golden files.
 * After an intended output change run: mvn -pl apidocs-core test -Dtest=GoldenSampleApiTest -Dgolden.update=true
 */
class GoldenSampleApiTest {

    private static final Path SAMPLE = Path.of("../examples/sample-api");
    private static final Path GOLDEN = Path.of("src/test/resources/golden/sample-api");
    private static final List<String> COMPARED = List.of("README.md", "technical-documentation.md", "api-reference.md",
            "architecture-overview.md", "openapi.yaml", "model.json");
    private static final List<String> PURPOSES = List.of("controller-CustomerController", "controller-OrderController",
            "controller-ProductController", "technical-doc", "architecture");

    @Test
    void sampleApiDocumentationMatchesTheGoldenFiles(@TempDir Path temp) throws IOException {
        Map<String, String> answers = new HashMap<>();
        for (String purpose : PURPOSES) {
            answers.put(purpose, resource("/fake-llm/sample-api/" + purpose + ".json"));
        }
        GeneratorConfig config = new ConfigResolver().resolve(new ConfigOverrides(SAMPLE, temp.resolve("out"), null,
                "dry-run", null, null, null, "pt-BR", temp.resolve("cache"), true, false), Map.of());

        PipelineResult result = new DocumentationPipeline(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
                .generate(config, FakeLlmClient.byPurpose(answers), ProgressListener.NONE, new CancellationToken());

        assertThat(result.llmCalls()).isEqualTo(5);
        assertThat(result.warnings()).extracting(Warning::code).containsExactly("LLM_UNKNOWN_ENDPOINT");
        boolean update = Boolean.getBoolean("golden.update");
        for (String name : COMPARED) {
            String actual = Files.readString(result.outputDir().resolve(name));
            Path golden = GOLDEN.resolve(name);
            if (update) {
                Files.createDirectories(GOLDEN);
                Files.writeString(golden, actual);
                continue;
            }
            assertThat(golden).as("golden file %s is missing; run once with -Dgolden.update=true", name).exists();
            assertThat(actual).as(name).isEqualTo(Files.readString(golden).replace("\r\n", "\n"));
        }
        String openapi = Files.readString(result.outputDir().resolve("openapi.yaml"));
        assertThat(new OpenAPIV3Parser().readContents(openapi, null, new ParseOptions()).getMessages()).isEmpty();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = GoldenSampleApiTest.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(in, path).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
