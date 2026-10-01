package dev.apidocs.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.ai.DryRunLlmClient;
import dev.apidocs.core.ai.LlmException;
import dev.apidocs.core.config.ConfigOverrides;
import dev.apidocs.core.config.ConfigResolver;
import dev.apidocs.core.config.GeneratorConfig;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.FakeLlmClient;
import dev.apidocs.core.testsupport.TinyProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationPipelineTest {

    @TempDir
    Path dir;

    private final DocumentationPipeline pipeline =
            new DocumentationPipeline(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    private GeneratorConfig config(Path project, boolean strict) {
        return new ConfigResolver().resolve(new ConfigOverrides(project, dir.resolve("out"), null, "dry-run", null,
                null, null, "en", dir.resolve("cache"), true, strict), Map.of());
    }

    private static FakeLlmClient narratives() {
        return FakeLlmClient.byPurpose(Map.of("controller-HelloController", TinyProject.CONTROLLER_NARRATIVE,
                "technical-doc", TinyProject.TECHNICAL_NARRATIVE, "architecture", TinyProject.ARCHITECTURE_NARRATIVE));
    }

    @Test
    void generatesEveryDeliverableAndReportsProgress() throws IOException {
        Path project = TinyProject.write(dir.resolve("tiny"));
        List<ProgressEvent> events = new ArrayList<>();

        PipelineResult result = pipeline.generate(config(project, false), narratives(), events::add, new CancellationToken());

        Path out = result.outputDir();
        assertThat(out).isEqualTo(dir.resolve("out").toAbsolutePath().normalize());
        for (String name : List.of("README.md", "technical-documentation.md", "api-reference.md",
                "architecture-overview.md", "openapi.yaml", "model.json", "generation-report.json")) {
            assertThat(out.resolve(name)).exists();
        }
        assertThat(out.resolve("prompts")).doesNotExist();
        assertThat(Files.readString(out.resolve("api-reference.md"))).contains("### `GET /hello/{name}` — Greet");
        assertThat(Files.readString(out.resolve("generation-report.json")))
                .contains("\"provider\": \"dry-run\"").contains("\"llmCalls\": 3");
        assertThat(result.llmCalls()).isEqualTo(3);
        assertThat(events).extracting(ProgressEvent::stage).containsExactly(Stage.SOURCE_LOADING, Stage.PARSING,
                Stage.EXTRACTION, Stage.CONTEXT_BUILDING, Stage.AI_PROCESSING, Stage.AI_PROCESSING,
                Stage.AI_PROCESSING, Stage.RENDERING, Stage.DELIVERY);
        assertThat(events.get(2).counters()).containsEntry("controllers", 1).containsEntry("endpoints", 1);
    }

    @Test
    void outputFolderHoldsExactlyTheFilesTheIndexLinksToAndOnlyTheReportIsTimestamped() throws IOException {
        Path project = TinyProject.write(dir.resolve("tiny"));

        Path out = pipeline.generate(config(project, false), narratives(), ProgressListener.NONE,
                new CancellationToken()).outputDir();

        List<String> names;
        try (Stream<Path> entries = Files.list(out)) {
            names = entries.map(path -> path.getFileName().toString()).toList();
        }
        assertThat(names).containsExactlyInAnyOrder("README.md", "technical-documentation.md", "api-reference.md",
                "architecture-overview.md", "openapi.yaml", "model.json", "generation-report.json");
        Matcher links = Pattern.compile("\\]\\(([^)#]+)\\)").matcher(Files.readString(out.resolve("README.md")));
        List<String> targets = new ArrayList<>();
        while (links.find()) {
            targets.add(links.group(1));
        }
        assertThat(targets).hasSize(5).allSatisfy(target -> assertThat(out.resolve(target)).isRegularFile());
        for (String name : names) {
            assertThat(Files.readString(out.resolve(name)).contains("2026-01-01"))
                    .as("timestamp in %s", name).isEqualTo(name.equals("generation-report.json"));
        }
    }

    @Test
    void dryRunWritesThePromptsAndAWarning() throws IOException {
        Path project = TinyProject.write(dir.resolve("tiny"));

        PipelineResult result = pipeline.generate(config(project, false), new DryRunLlmClient(),
                ProgressListener.NONE, new CancellationToken());

        assertThat(result.outputDir().resolve("prompts/01-controller-HelloController.md")).exists();
        assertThat(result.outputDir().resolve("prompts/03-architecture.md")).exists();
        assertThat(result.warnings()).extracting(Warning::code).contains("DRY_RUN");
        assertThat(Files.readString(result.outputDir().resolve("README.md"))).contains("Narrative texts: dry-run.");
    }

    @Test
    void regeneratesOverItsOwnPreviousOutputIncludingPrompts() {
        Path project = TinyProject.write(dir.resolve("tiny"));
        pipeline.generate(config(project, false), new DryRunLlmClient(), ProgressListener.NONE, new CancellationToken());
        assertThat(dir.resolve("out/prompts")).isDirectory();

        PipelineResult second = pipeline.generate(config(project, false), narratives(), ProgressListener.NONE,
                new CancellationToken());

        assertThat(second.outputDir().resolve("prompts")).doesNotExist();
        assertThat(second.outputDir().resolve("generation-report.json")).exists();
    }

    @Test
    void strictModeTurnsLlmFailuresIntoErrors() {
        Path project = TinyProject.write(dir.resolve("tiny"));
        FakeLlmClient garbage = FakeLlmClient.byPurpose(Map.of());

        assertThatThrownBy(() -> pipeline.generate(config(project, true), garbage, ProgressListener.NONE,
                new CancellationToken())).isInstanceOf(LlmException.class).hasMessageContaining("--strict");
        assertThat(dir.resolve("out")).doesNotExist();

        PipelineResult lenient = pipeline.generate(config(project, false), FakeLlmClient.byPurpose(Map.of()),
                ProgressListener.NONE, new CancellationToken());
        assertThat(lenient.warnings()).extracting(Warning::code).contains("LLM_OUTPUT_INVALID");
    }

    @Test
    void failsWithoutControllers() throws IOException {
        Path project = dir.resolve("plain");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("src/main/java/A.java"), "public record A(String x) {}");

        assertThatThrownBy(() -> pipeline.generate(config(project, false), new DryRunLlmClient(),
                ProgressListener.NONE, new CancellationToken()))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("No @RestController");
    }

    @Test
    void cancellationLeavesNoOutput() {
        Path project = TinyProject.write(dir.resolve("tiny"));
        CancellationToken token = new CancellationToken();
        token.cancel();

        assertThatThrownBy(() -> pipeline.generate(config(project, false), narratives(), ProgressListener.NONE, token))
                .isInstanceOf(CancelledException.class);
        assertThat(dir.resolve("out")).doesNotExist();
    }
}
