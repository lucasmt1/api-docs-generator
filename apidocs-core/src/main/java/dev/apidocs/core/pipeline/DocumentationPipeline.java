package dev.apidocs.core.pipeline;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.ApiDocsVersion;
import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.ai.DryRunLlmClient;
import dev.apidocs.core.ai.LlmClient;
import dev.apidocs.core.ai.LlmException;
import dev.apidocs.core.ai.LlmSettings;
import dev.apidocs.core.analysis.JavaSourceParser;
import dev.apidocs.core.analysis.ParseOutcome;
import dev.apidocs.core.config.GeneratorConfig;
import dev.apidocs.core.extraction.ModelAssembler;
import dev.apidocs.core.generation.ApiReferenceDocument;
import dev.apidocs.core.generation.ArchitectureAlert;
import dev.apidocs.core.generation.ArchitectureDocument;
import dev.apidocs.core.generation.ArchitectureRules;
import dev.apidocs.core.generation.DocumentContext;
import dev.apidocs.core.generation.IndexDocument;
import dev.apidocs.core.generation.Messages;
import dev.apidocs.core.generation.NarrativeGenerator;
import dev.apidocs.core.generation.NarrativeResult;
import dev.apidocs.core.generation.OpenApiDocument;
import dev.apidocs.core.generation.TechnicalDocument;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.model.Warnings;
import dev.apidocs.core.source.LoadedSources;
import dev.apidocs.core.source.ProjectInfoReader;
import dev.apidocs.core.source.SourceLoader;
import dev.apidocs.core.support.JsonSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs the whole reference diagram: sources → analysis → extraction → AI → delivery. */
public final class DocumentationPipeline {

    private static final Set<String> LLM_FAILURES = Set.of("LLM_CALL_FAILED", "LLM_OUTPUT_INVALID", "LLM_REFUSED");

    private final Clock clock;

    public DocumentationPipeline() {
        this(Clock.systemUTC());
    }

    public DocumentationPipeline(Clock clock) {
        this.clock = clock;
    }

    public ApiModel analyze(GeneratorConfig config, ProgressListener progress, CancellationToken cancellation) {
        // Same four steps as ModelExtractor.extract (its progress-less equivalent), split to emit progress between them.
        cancellation.throwIfCancelled();
        LoadedSources sources = new SourceLoader().load(config.projectDir(), config.exclude(), config.limits());
        progress.onEvent(new ProgressEvent(Stage.SOURCE_LOADING, "Java sources loaded",
                counters("files", sources.javaFiles().size()), 0, 0));

        cancellation.throwIfCancelled();
        ParseOutcome parsed = new JavaSourceParser().parse(sources.javaFiles());
        progress.onEvent(new ProgressEvent(Stage.PARSING, "Syntax trees built",
                counters("parsed", parsed.units().size(), "errors", parsed.warnings().size()), 0, 0));

        cancellation.throwIfCancelled();
        ApiModel model = new ModelAssembler().assemble(new ProjectInfoReader().read(config.projectDir()), parsed,
                sources.warnings());
        progress.onEvent(new ProgressEvent(Stage.EXTRACTION, "Layers extracted", counters(
                "controllers", model.controllers().size(),
                "endpoints", model.endpointCount(),
                "dtos", (int) model.schemas().stream().filter(schema -> !schema.entity()).count(),
                "services", model.services().size(),
                "entities", model.entities().size()), 0, 0));
        return model;
    }

    public PipelineResult generate(GeneratorConfig config, LlmClient llm, ProgressListener progress,
            CancellationToken cancellation) {
        Instant start = clock.instant();
        OutputWriter writer = new OutputWriter();
        writer.verifyTarget(config.outputDir());
        ApiModel model = analyze(config, progress, cancellation);
        if (model.controllers().isEmpty()) {
            throw new AnalysisException("No @RestController found in " + config.projectDir()
                    + ". Is this a Spring Boot project?");
        }

        cancellation.throwIfCancelled();
        List<ArchitectureAlert> alerts = new ArchitectureRules().evaluate(model);
        progress.onEvent(new ProgressEvent(Stage.CONTEXT_BUILDING, "Context prepared", counters("alerts", alerts.size()), 0, 0));

        NarrativeResult narrative = new NarrativeGenerator(llm).generate(model, alerts,
                new NarrativeGenerator.Settings(config.language(), config.context(), config.maxInputTokens(),
                        config.maxOutputTokens()),
                (step, total, label) -> progress.onEvent(new ProgressEvent(Stage.AI_PROCESSING, label, Map.of(), step, total)),
                cancellation);

        List<Warning> collected = new ArrayList<>(model.warnings());
        collected.addAll(narrative.warnings());
        if (llm instanceof DryRunLlmClient) {
            collected.add(Warning.of("DRY_RUN", "No LLM was called; narrative sections contain placeholders."));
        }
        List<Warning> warnings = Warnings.sortedDistinct(collected);
        if (config.strict()) {
            List<Warning> failures = warnings.stream().filter(w -> LLM_FAILURES.contains(w.code())).toList();
            if (!failures.isEmpty()) {
                throw new LlmException("The LLM could not produce " + failures.size()
                        + " section(s) (first: " + failures.get(0).message() + ") and --strict is set.", false);
            }
        }

        cancellation.throwIfCancelled();
        Map<String, String> files = render(config, llm, model, alerts, narrative, warnings, start);
        progress.onEvent(new ProgressEvent(Stage.RENDERING, "Documents rendered", counters("files", files.size()), 0, 0));

        cancellation.throwIfCancelled();
        writer.writeAtomically(config.outputDir(), files);
        progress.onEvent(ProgressEvent.of(Stage.DELIVERY, config.outputDir().toString()));
        return new PipelineResult(config.outputDir(), model, warnings, narrative.usage(), narrative.llmCalls(),
                Duration.between(start, clock.instant()));
    }

    private Map<String, String> render(GeneratorConfig config, LlmClient llm, ApiModel model,
            List<ArchitectureAlert> alerts, NarrativeResult narrative, List<Warning> warnings, Instant start) {
        DocumentContext context = new DocumentContext(model, narrative.narratives(), alerts,
                Messages.forLanguage(config.language()), narrativeSource(config.llm()));
        List<DryRunLlmClient.RecordedPrompt> prompts =
                llm instanceof DryRunLlmClient dryRun ? dryRun.recordedPrompts() : List.of();
        Map<String, String> files = new LinkedHashMap<>();
        files.put(OutputFiles.README, new IndexDocument().render(context, warnings, !prompts.isEmpty()));
        files.put(OutputFiles.TECHNICAL_DOCUMENTATION, new TechnicalDocument().render(context));
        files.put(OutputFiles.API_REFERENCE, new ApiReferenceDocument().render(context));
        files.put(OutputFiles.ARCHITECTURE_OVERVIEW, new ArchitectureDocument().render(context));
        files.put(OutputFiles.OPENAPI, new OpenApiDocument().render(model, narrative.narratives()));
        files.put(OutputFiles.MODEL, JsonSupport.toPrettyJson(model));
        Instant end = clock.instant();
        files.put(OutputFiles.REPORT, JsonSupport.toPrettyJson(new GenerationReport(ApiDocsVersion.get(),
                config.llm().provider().id(), config.llm().model(), config.language(), end.toString(),
                Duration.between(start, end).toMillis(), narrative.llmCalls(), narrative.usage(), warnings)));
        if (!prompts.isEmpty()) {
            prompts.forEach(prompt -> files.put(OutputFiles.PROMPTS_DIR + "/" + prompt.fileName(), prompt.toMarkdown()));
            // the prompts quote the source code: keep them out of anything that commits the output folder
            files.put(OutputFiles.PROMPTS_DIR + "/" + OutputFiles.PROMPTS_GITIGNORE, OutputFiles.PROMPTS_GITIGNORE_CONTENT);
        }
        return files;
    }

    /**
     * Provider and model as configured, the values generation-report.json records (e.g. {@code gemini /
     * gemini-3.8-flash}). Never the client's id: for OpenAI-compatible servers it names the endpoint's host, which
     * may be internal and does not belong in published documents.
     */
    static String narrativeSource(LlmSettings llm) {
        String provider = llm.provider().id();
        String model = llm.model() == null ? "" : llm.model();
        return model.isBlank() || model.equals(provider) ? provider : provider + " / " + model;
    }

    private static Map<String, Integer> counters(Object... keysAndValues) {
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            counters.put((String) keysAndValues[i], (Integer) keysAndValues[i + 1]);
        }
        return counters;
    }
}
