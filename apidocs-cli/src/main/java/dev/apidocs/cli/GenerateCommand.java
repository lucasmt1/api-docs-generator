package dev.apidocs.cli;

import dev.apidocs.core.ApiDocsVersion;
import dev.apidocs.core.ai.LlmClient;
import dev.apidocs.core.config.ConfigOverrides;
import dev.apidocs.core.config.ConfigResolver;
import dev.apidocs.core.config.GeneratorConfig;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.pipeline.PipelineResult;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "generate", mixinStandardHelpOptions = true, versionProvider = ApiDocsCli.VersionProvider.class,
        description = "Analyze a Spring Boot project and generate its documentation.")
final class GenerateCommand implements Callable<Integer> {

    private static final int MAX_LISTED_WARNINGS = 10;

    private final CliContext context;

    @Parameters(index = "0", paramLabel = "<project-dir>", description = "Root directory of the Spring Boot project.")
    Path projectDir;

    @Option(names = {"-o", "--output"}, description = "Output directory (default: ./api-docs).")
    Path output;

    @Option(names = "--provider", description = "gemini, ollama, openai, anthropic, custom or dry-run (default: dry-run).")
    String provider;

    @Option(names = "--model", description = "Model id (default: the provider's default).")
    String model;

    @Option(names = "--base-url", description = "Base URL of an OpenAI-compatible server (custom provider).")
    String baseUrl;

    @Option(names = "--api-key-env", description = "Name of the environment variable that holds the API key.")
    String apiKeyEnv;

    @Option(names = "--language", description = "Language of the generated texts (default: pt-BR).")
    String language;

    @Option(names = "--config", description = "Path to a .apidocs.yml file (default: <project-dir>/.apidocs.yml).")
    Path config;

    @Option(names = "--cache-dir", description = "LLM response cache directory (default: ./.apidocs-cache).")
    Path cacheDir;

    @Option(names = "--no-cache", description = "Do not read or write the LLM response cache.")
    boolean noCache;

    @Option(names = "--strict", description = "Exit with code 3 when the LLM cannot produce a section.")
    boolean strict;

    @Option(names = {"-v", "--verbose"}, description = "Show debug logs and stack traces.")
    boolean verbose;

    GenerateCommand(CliContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        Logging.configure(verbose);
        GeneratorConfig resolved = new ConfigResolver().resolve(new ConfigOverrides(projectDir, output, config, provider,
                model, baseUrl, apiKeyEnv, language, cacheDir, noCache, strict), context.environment());
        LlmClient llm = context.llmFactory().create(resolved.llm(), context.environment(), resolved.cacheDir(),
                resolved.cacheEnabled());
        PrintWriter out = context.out();
        out.println("apidocs " + ApiDocsVersion.get() + " | " + resolved.projectDir() + " -> " + resolved.outputDir()
                + " | " + llm.id());
        PipelineResult result = context.pipeline().get()
                .generate(resolved, llm, new ConsoleProgress(out), context.cancellation());
        printSummary(out, result);
        return ExitCodes.OK;
    }

    private static void printSummary(PrintWriter out, PipelineResult result) {
        out.printf(Locale.ROOT, "Done in %.1f s: %d LLM call(s), %d input / %d output tokens (%d cached).%n",
                result.duration().toMillis() / 1000.0, result.llmCalls(), result.usage().inputTokens(),
                result.usage().outputTokens(), result.usage().cachedInputTokens());
        List<Warning> warnings = result.warnings();
        if (!warnings.isEmpty()) {
            out.println(warnings.size() + " warning(s):");
            warnings.stream().limit(MAX_LISTED_WARNINGS).forEach(w -> out.println("  ! " + w.code()
                    + (w.location().isBlank() ? "" : " [" + w.location() + "]") + ": " + w.message()));
            if (warnings.size() > MAX_LISTED_WARNINGS) {
                out.println("  ... see README.md in the output folder for the full list.");
            }
        }
        out.flush();
    }
}
