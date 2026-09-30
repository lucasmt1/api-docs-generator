package dev.apidocs.core.generation;

import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.ai.LlmClient;
import dev.apidocs.core.ai.PromptTemplates;
import dev.apidocs.core.ai.StructuredGenerator;
import dev.apidocs.core.ai.TokenUsage;
import dev.apidocs.core.ai.narrative.ArchitectureNarrative;
import dev.apidocs.core.ai.narrative.ControllerNarrative;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import dev.apidocs.core.config.ContextConfig;
import dev.apidocs.core.context.ContextBudget;
import dev.apidocs.core.context.ContextRenderer;
import dev.apidocs.core.context.ControllerSlice;
import dev.apidocs.core.context.ServiceMethodRef;
import dev.apidocs.core.context.SliceBuilder;
import dev.apidocs.core.context.TokenEstimator;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** AI Processing stage: one call per controller, then Technical Documentation and Architectural Overview. */
public final class NarrativeGenerator {

    public record Settings(String language, ContextConfig context, int maxInputTokens, int maxOutputTokens) {
    }

    @FunctionalInterface
    public interface StepListener {

        StepListener NONE = (step, total, label) -> {
        };

        void onStep(int step, int total, String label);
    }

    private static final String DEFAULT_AUDIENCE = "developers who integrate with or maintain this API";

    private final StructuredGenerator generator;
    private final PromptTemplates templates = new PromptTemplates();
    private final ContextRenderer renderer = new ContextRenderer();
    private final SliceBuilder slices = new SliceBuilder();
    private final ContextBudget budget = new ContextBudget();

    public NarrativeGenerator(LlmClient client) {
        this.generator = new StructuredGenerator(client);
    }

    public NarrativeResult generate(ApiModel model, List<ArchitectureAlert> alerts, Settings settings,
            StepListener listener, CancellationToken cancellation) {
        cancellation.throwIfCancelled();
        String system = templates.render("system", systemContext(model, settings));
        int available = Math.max(1_000, settings.maxInputTokens() - TokenEstimator.estimate(system));
        int total = model.controllers().size() + 2;
        int step = 0;
        Totals totals = new Totals();
        ExampleSanitizer sanitizer = new ExampleSanitizer(model.schemasByName());

        Map<String, ControllerNarrative> controllers = new LinkedHashMap<>();
        for (ControllerInfo controller : model.controllers()) {
            cancellation.throwIfCancelled();
            listener.onStep(++step, total, controller.name());
            ControllerSlice slice = slices.slice(model, controller);
            ContextBudget.Fitted fitted = budget.fit(
                    methods -> templates.render("controller", controllerContext(slice, methods)),
                    slice.serviceMethods(), available);
            if (fitted.truncated()) {
                totals.warn(new Warning("CONTEXT_TRUNCATED",
                        "Service method bodies were shortened to fit the context budget", controller.name()));
            }
            if (fitted.overBudget()) {
                totals.warn(new Warning("CONTEXT_OVER_BUDGET",
                        "The prompt is larger than the provider context budget", controller.name()));
            }
            StructuredGenerator.Result<ControllerNarrative> result = generator.generate(
                    "controller-" + controller.name(), system, fitted.text(), ControllerNarrative.class,
                    settings.maxOutputTokens());
            totals.add(result);
            result.value().ifPresent(narrative ->
                    controllers.put(controller.name(), clean(narrative, controller, sanitizer, totals)));
        }

        cancellation.throwIfCancelled();
        listener.onStep(++step, total, "technical-doc");
        String technicalPrompt = templates.render("technical-doc", technicalContext(model, controllers, true));
        if (TokenEstimator.estimate(technicalPrompt) > available) {
            technicalPrompt = templates.render("technical-doc", technicalContext(model, controllers, false));
            totals.warn(new Warning("CONTEXT_TRUNCATED",
                    "Schemas were left out of the prompt to fit the context budget", "technical-doc"));
        }
        StructuredGenerator.Result<TechnicalDocNarrative> technical = generator.generate("technical-doc", system,
                technicalPrompt, TechnicalDocNarrative.class, settings.maxOutputTokens());
        totals.add(technical);

        cancellation.throwIfCancelled();
        listener.onStep(++step, total, "architecture");
        StructuredGenerator.Result<ArchitectureNarrative> architecture = generator.generate("architecture", system,
                templates.render("architecture", Map.of("components", renderer.components(model),
                        "metrics", renderer.metrics(model), "alerts", renderer.alerts(alerts))),
                ArchitectureNarrative.class, settings.maxOutputTokens());
        totals.add(architecture);

        return new NarrativeResult(new NarrativeSet(controllers, technical.value().orElse(null),
                architecture.value().orElse(null)), totals.usage, totals.calls, totals.warnings);
    }

    private static final class Totals {
        private TokenUsage usage = TokenUsage.ZERO;
        private int calls;
        private final List<Warning> warnings = new ArrayList<>();

        void add(StructuredGenerator.Result<?> result) {
            usage = usage.plus(result.usage());
            calls += result.calls();
            warnings.addAll(result.warnings());
        }

        void warn(Warning warning) {
            warnings.add(warning);
        }
    }

    private ControllerNarrative clean(ControllerNarrative narrative, ControllerInfo controller,
            ExampleSanitizer sanitizer, Totals totals) {
        Map<String, EndpointInfo> endpoints = new LinkedHashMap<>();
        controller.endpoints().forEach(endpoint -> endpoints.put(endpoint.id(), endpoint));
        Map<String, EndpointNarrative> kept = new LinkedHashMap<>();
        for (EndpointNarrative item : narrative.endpoints()) {
            EndpointInfo endpoint = endpoints.get(item.endpointId());
            if (endpoint == null) {
                totals.warn(new Warning("LLM_UNKNOWN_ENDPOINT",
                        "Ignored text for unknown endpoint " + item.endpointId(), controller.name()));
                continue;
            }
            if (kept.containsKey(endpoint.id())) {
                continue;
            }
            String request = example(item.requestExample(),
                    endpoint.requestBody() == null ? null : endpoint.requestBody().type(),
                    endpoint.id() + " request example", sanitizer, totals);
            String response = example(item.responseExample(), endpoint.response().body(),
                    endpoint.id() + " response example", sanitizer, totals);
            kept.put(endpoint.id(), item.withExamples(request, response));
        }
        endpoints.keySet().stream()
                .filter(id -> !kept.containsKey(id))
                .forEach(id -> totals.warn(new Warning("LLM_MISSING_ENDPOINT",
                        "No text was generated for this endpoint", id)));
        return new ControllerNarrative(narrative.controllerSummary(), List.copyOf(kept.values()));
    }

    private static String example(String example, TypeRef type, String location, ExampleSanitizer sanitizer,
            Totals totals) {
        ExampleSanitizer.Result result = sanitizer.sanitize(example, type);
        if (result.invalid()) {
            totals.warn(new Warning("EXAMPLE_INVALID",
                    "Example discarded: not valid JSON for the documented schema", location));
        }
        return result.json();
    }

    private Map<String, Object> systemContext(ApiModel model, Settings settings) {
        ContextConfig context = settings.context();
        Map<String, Object> values = new HashMap<>();
        values.put("audience", context.audience().isBlank() ? DEFAULT_AUDIENCE : context.audience());
        values.put("language", languageName(settings.language()));
        values.put("hasProjectContext", !context.isEmpty());
        values.put("description", context.description());
        values.put("glossary", context.glossary().entrySet().stream()
                .map(entry -> Map.of("term", entry.getKey(), "definition", entry.getValue()))
                .toList());
        values.put("instructions", context.instructions());
        values.put("projectOverview", renderer.projectOverview(model));
        return values;
    }

    private Map<String, Object> controllerContext(ControllerSlice slice, List<ServiceMethodRef> methods) {
        return Map.of(
                "endpointIds", slice.controller().endpoints().stream().map(EndpointInfo::id)
                        .collect(Collectors.joining(", ")),
                "endpoints", renderer.endpoints(slice.controller()),
                "schemas", renderer.schemas(slice.schemas()),
                "serviceMethods", renderer.serviceMethods(methods),
                "exceptionMappings", renderer.exceptionMappings(slice.exceptionMappings()));
    }

    private Map<String, Object> technicalContext(ApiModel model, Map<String, ControllerNarrative> narratives,
            boolean includeSchemas) {
        return Map.of(
                "structure", renderer.structure(model),
                "endpointSummaries", endpointSummaries(model, narratives),
                "schemas", includeSchemas ? renderer.schemas(model.schemas()) : "(omitted to fit the context budget)",
                "entities", renderer.entities(model.entities()),
                "exceptionMappings", renderer.exceptionMappings(model.exceptionMappings()));
    }

    private static String endpointSummaries(ApiModel model, Map<String, ControllerNarrative> narratives) {
        List<String> lines = new ArrayList<>();
        for (ControllerInfo controller : model.controllers()) {
            Map<String, EndpointNarrative> byId = new HashMap<>();
            if (narratives.containsKey(controller.name())) {
                narratives.get(controller.name()).endpoints().forEach(n -> byId.put(n.endpointId(), n));
            }
            for (EndpointInfo endpoint : controller.endpoints()) {
                StringBuilder line = new StringBuilder("- [").append(endpoint.id()).append("] ")
                        .append(endpoint.method()).append(' ').append(endpoint.path());
                EndpointNarrative narrative = byId.get(endpoint.id());
                if (narrative != null) {
                    line.append(": ").append(narrative.summary());
                    if (!narrative.businessRules().isEmpty()) {
                        line.append("\n  rules: ").append(String.join("; ", narrative.businessRules()));
                    }
                }
                lines.add(line.toString());
            }
        }
        return lines.isEmpty() ? "(none)" : String.join("\n", lines);
    }

    static String languageName(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String name = locale.getDisplayName(Locale.ENGLISH);
        return name.isBlank() ? languageTag : name + " (" + languageTag + ")";
    }
}
