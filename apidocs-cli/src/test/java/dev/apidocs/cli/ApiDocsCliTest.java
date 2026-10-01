package dev.apidocs.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.ai.LlmClientFactory;
import dev.apidocs.core.ai.LlmException;
import dev.apidocs.core.pipeline.DocumentationPipeline;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiDocsCliTest {

    private static final String SAMPLE = Path.of("../examples/sample-api").toString();

    @TempDir
    Path dir;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(Map<String, String> environment, String... args) {
        CliContext context = new CliContext(environment, new PrintWriter(out, true), new PrintWriter(err, true),
                new LlmClientFactory(), DocumentationPipeline::new, new CancellationToken());
        return ApiDocsCli.execute(args, context);
    }

    @Test
    void printsTheVersion() {
        assertThat(run(Map.of(), "--version")).isZero();
        assertThat(out.toString()).startsWith("apidocs ");
    }

    @Test
    void reportsUsageErrorsWithExitCodeOne() {
        assertThat(run(Map.of())).isEqualTo(1);
        assertThat(err.toString()).contains("Missing command");
        assertThat(run(Map.of(), "generate", "--bogus", SAMPLE)).isEqualTo(1);
    }

    @Test
    void generatesTheDocumentationInDryRunMode() {
        int code = run(Map.of(), "generate", SAMPLE, "--output", dir.resolve("docs").toString(), "--no-cache");

        assertThat(code).as(err.toString()).isZero();
        assertThat(dir.resolve("docs/api-reference.md")).exists();
        assertThat(dir.resolve("docs/prompts/01-controller-CustomerController.md")).exists();
        assertThat(out.toString())
                .contains("Static analysis")
                .contains("AI processing (5/5)")
                .contains("DRY_RUN");
    }

    @Test
    void explainsMissingApiKeys() {
        int code = run(Map.of(), "generate", SAMPLE, "-o", dir.resolve("docs").toString(), "--provider", "gemini");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("GEMINI_API_KEY").contains("--provider dry-run");
    }

    @Test
    void usesExitCodeTwoForAnalysisProblems() throws IOException {
        assertThat(run(Map.of(), "generate", dir.resolve("nope").toString())).isEqualTo(2);
        assertThat(err.toString()).contains("Project directory not found");

        Path plain = Files.createDirectories(dir.resolve("plain/src/main/java"));
        Files.writeString(plain.resolve("A.java"), "public record A(String x) {}");
        assertThat(run(Map.of(), "generate", dir.resolve("plain").toString(), "-o", dir.resolve("docs").toString()))
                .isEqualTo(2);
        assertThat(err.toString()).contains("No @RestController");
    }

    @Test
    void rejectsSecretsInTheConfigFile() throws IOException {
        Path config = Files.writeString(dir.resolve("bad.yml"), "llm:\n  apiKey: abc\n");

        int code = run(Map.of(), "generate", SAMPLE, "--config", config.toString(), "-o", dir.resolve("docs").toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("Secrets are not allowed");
    }

    @Test
    void analyzesToStandardOutputOrToAFile() {
        assertThat(run(Map.of(), "analyze", SAMPLE)).isZero();
        assertThat(out.toString()).contains("\"controllers\"").contains("OrderController");

        assertThat(run(Map.of(), "analyze", SAMPLE, "-o", dir.resolve("model.json").toString())).isZero();
        assertThat(dir.resolve("model.json")).exists();
    }

    @Test
    void usesExitCodeOneThirtyWhenCancelled() {
        CancellationToken cancelled = new CancellationToken();
        cancelled.cancel();
        CliContext context = new CliContext(Map.of(), new PrintWriter(out, true), new PrintWriter(err, true),
                new LlmClientFactory(), DocumentationPipeline::new, cancelled);

        int code = ApiDocsCli.execute(new String[] {"generate", SAMPLE, "-o", dir.resolve("docs").toString()}, context);

        assertThat(code).isEqualTo(130);
        assertThat(err.toString()).contains("Cancelled.");
        assertThat(dir.resolve("docs")).doesNotExist();
    }

    @Test
    void usesExitCodeThreeForLlmFailures() {
        int code = ApiDocsCli.handle(new LlmException("The LLM could not produce 1 section(s).", false),
                new PrintWriter(err, true), false);

        assertThat(code).isEqualTo(3);
        assertThat(err.toString()).startsWith("Error: The LLM could not produce");
    }

    @Test
    void neverPrintsTheApiKeyValue() {
        String secret = "SECRET-key-value-123";
        // The trailing line break is not a valid header character: the adapter rejects the key while it is being
        // built, so no request is made. Verbose mode also prints the stack trace and its causes.
        int code = run(Map.of("APIDOCS_KEY", secret + "\n"), "generate", SAMPLE, "-o", dir.resolve("docs").toString(),
                "--provider", "custom", "--model", "m", "--base-url", "http://localhost:1/v1",
                "--api-key-env", "APIDOCS_KEY", "-v");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).contains("Invalid configuration").doesNotContain(secret);
        assertThat(out.toString()).doesNotContain(secret);
    }

    /** picocli does not route JVM errors (e.g. a StackOverflowError from deeply nested sources) to its handlers. */
    private int runWithPipelineThrowingAnError(String... args) {
        CliContext context = new CliContext(Map.of(), new PrintWriter(out, true), new PrintWriter(err, true),
                new LlmClientFactory(), () -> {
                    throw new StackOverflowError();
                }, new CancellationToken());
        return assertTimeoutPreemptively(Duration.ofSeconds(5), () -> ApiDocsCli.execute(args, context));
    }

    @Test
    void reportsJvmErrorsAsUnexpectedFailuresWithExitCodeOne() {
        int code = runWithPipelineThrowingAnError("generate", SAMPLE, "-o", dir.resolve("docs").toString());

        assertThat(code).isEqualTo(1);
        assertThat(err.toString().strip()).isEqualTo("Unexpected error: java.lang.StackOverflowError");

        err.getBuffer().setLength(0);
        assertThat(runWithPipelineThrowingAnError("analyze", SAMPLE)).isEqualTo(1);
        assertThat(err.toString().strip()).isEqualTo("Unexpected error: java.lang.StackOverflowError");
    }

    @Test
    void printsTheStackTraceOfJvmErrorsOnlyInVerboseMode() {
        int code = runWithPipelineThrowingAnError("generate", SAMPLE, "-o", dir.resolve("docs").toString(), "-v");

        assertThat(code).isEqualTo(1);
        assertThat(err.toString()).startsWith("Unexpected error: java.lang.StackOverflowError")
                .contains("GenerateCommand.call");
    }

    @Test
    void verboseModeShowsDebugLogsOfTheToolButNotOfTheJdk() {
        Logger tool = Logger.getLogger("dev.apidocs.core.pipeline.Anything");
        Logger jdk = Logger.getLogger("jdk.event.security");

        assertThat(run(Map.of(), "analyze", SAMPLE, "-v")).isZero();
        assertThat(tool.isLoggable(Level.FINE)).isTrue();
        assertThat(jdk.isLoggable(Level.FINE)).isFalse();

        assertThat(run(Map.of(), "analyze", SAMPLE)).isZero();
        assertThat(tool.isLoggable(Level.FINE)).isFalse();
    }

    @Test
    void printsTheVersionFromTheSubcommandsToo() {
        assertThat(run(Map.of(), "generate", "--version")).isZero();
        assertThat(run(Map.of(), "analyze", "-V")).isZero();

        assertThat(out.toString().lines().toList()).hasSize(2).allMatch(line -> line.startsWith("apidocs "));
    }
}
