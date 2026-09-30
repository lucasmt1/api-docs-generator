package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.apidocs.core.CancellationToken;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.ai.LlmRequest;
import dev.apidocs.core.ai.TokenUsage;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.config.ContextConfig;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.FakeLlmClient;
import dev.apidocs.core.testsupport.ModelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NarrativeGeneratorTest {

    static final String CONTROLLER = """
            {"controllerSummary": "Manages orders.",
             "endpoints": [
               {"endpointId": "OrderController#create", "title": "Create order", "summary": "Creates an order.",
                "description": "Reserves stock and saves the order.", "businessRules": ["At least one item"],
                "errorScenarios": [{"status": 409, "when": "A product has no stock"}],
                "requestExample": "{\\"customerId\\": 1, \\"hack\\": true}", "responseExample": "not json"},
               {"endpointId": "OrderController#ghost", "title": "x", "summary": "x", "description": "x",
                "businessRules": [], "errorScenarios": [], "requestExample": "", "responseExample": ""}
             ]}
            """;
    static final String TECHNICAL = """
            {"overview": "Orders system.", "domainConcepts": [{"name": "Order", "description": "A purchase."}],
             "businessRules": [{"domain": "Orders", "rules": ["Stock is reserved"]}],
             "errorHandling": "Errors return ApiError.", "glossary": [{"term": "SKU", "definition": "Stock keeping unit"}]}
            """;
    static final String ARCHITECTURE = """
            {"summary": "Layered.", "layers": [{"layer": "Controllers", "description": "HTTP"}],
             "patterns": ["DTO mapping"],
             "alertComments": [{"code": "ENTITY_EXPOSED", "subject": "Customer", "comment": "Leaks internals.", "recommendation": "Use a DTO."}],
             "recommendations": ["Add a CustomerResponse DTO"]}
            """;

    private final ApiModel model = ModelFixtures.orderApi();
    private final List<ArchitectureAlert> alerts = new ArchitectureRules().evaluate(model);
    private final NarrativeGenerator.Settings settings = new NarrativeGenerator.Settings("pt-BR",
            new ContextConfig("Shop backend", "", Map.of(), ""), 200_000, 16_000);

    @Test
    void makesOneCallPerControllerPlusTwoAndCleansTheAnswers() {
        FakeLlmClient llm = FakeLlmClient.byPurpose(Map.of("controller-OrderController", CONTROLLER,
                "technical-doc", TECHNICAL, "architecture", ARCHITECTURE));
        List<String> steps = new ArrayList<>();

        NarrativeResult result = new NarrativeGenerator(llm).generate(model, alerts, settings,
                (step, total, label) -> steps.add(step + "/" + total + " " + label), new CancellationToken());

        assertThat(llm.requests()).extracting(LlmRequest::purpose)
                .containsExactly("controller-OrderController", "technical-doc", "architecture");
        assertThat(steps).containsExactly("1/3 OrderController", "2/3 technical-doc", "3/3 architecture");
        assertThat(result.llmCalls()).isEqualTo(3);
        assertThat(result.usage()).isEqualTo(new TokenUsage(300, 150, 0));
        NarrativeSet narratives = result.narratives();
        assertThat(narratives.controller("OrderController").orElseThrow().controllerSummary()).isEqualTo("Manages orders.");
        EndpointNarrative create = narratives.endpoint("OrderController", "OrderController#create").orElseThrow();
        assertThat(create.requestExample()).isEqualTo("{\n  \"customerId\": 1\n}");
        assertThat(create.responseExample()).isEmpty();
        assertThat(narratives.endpoint("OrderController", "OrderController#ghost")).isEmpty();
        assertThat(narratives.technicalDoc()).isPresent();
        assertThat(narratives.architectureDoc()).isPresent();
        assertThat(result.warnings()).extracting(Warning::code, Warning::location).containsExactlyInAnyOrder(
                tuple("EXAMPLE_INVALID", "OrderController#create response example"),
                tuple("LLM_UNKNOWN_ENDPOINT", "OrderController"),
                tuple("LLM_MISSING_ENDPOINT", "OrderController#get"),
                tuple("LLM_MISSING_ENDPOINT", "OrderController#customer"));
    }

    @Test
    void buildsPromptsWithAStableSystemPrefix() {
        FakeLlmClient llm = FakeLlmClient.byPurpose(Map.of("controller-OrderController", CONTROLLER,
                "technical-doc", TECHNICAL, "architecture", ARCHITECTURE));

        new NarrativeGenerator(llm).generate(model, alerts, settings, NarrativeGenerator.StepListener.NONE,
                new CancellationToken());

        LlmRequest controller = llm.requests().get(0);
        assertThat(controller.systemPrompt())
                .contains("Write every natural-language value in Portuguese (Brazil) (pt-BR).")
                .contains("- Description: Shop backend")
                .contains("- Name: Orders API");
        assertThat(controller.userPrompt())
                .contains("OrderController#create, OrderController#get, OrderController#customer")
                .contains("<source_code file=\"OrderService#create\">");
        assertThat(llm.requests().get(1).userPrompt())
                .contains("- [OrderController#create] POST /api/orders: Creates an order.\n  rules: At least one item");
        assertThat(llm.requests().get(2).userPrompt()).contains("- [ENTITY_EXPOSED] Customer");
        assertThat(llm.requests()).extracting(LlmRequest::systemPrompt).containsOnly(controller.systemPrompt());
        assertThat(controller.maxOutputTokens()).isEqualTo(16_000);
    }

    @Test
    void keepsGoingWhenAControllerCallFails() {
        FakeLlmClient llm = FakeLlmClient.byPurpose(Map.of("controller-OrderController", "garbage",
                "technical-doc", TECHNICAL, "architecture", ARCHITECTURE));

        NarrativeResult result = new NarrativeGenerator(llm).generate(model, alerts, settings,
                NarrativeGenerator.StepListener.NONE, new CancellationToken());

        assertThat(result.narratives().controller("OrderController")).isEmpty();
        assertThat(result.narratives().technicalDoc()).isPresent();
        assertThat(result.llmCalls()).isEqualTo(4);
        assertThat(result.warnings()).extracting(Warning::code).contains("LLM_OUTPUT_INVALID");
    }

    @Test
    void stopsWhenCancelled() {
        FakeLlmClient llm = FakeLlmClient.replying();
        CancellationToken token = new CancellationToken();
        token.cancel();

        assertThatThrownBy(() -> new NarrativeGenerator(llm).generate(model, alerts, settings,
                NarrativeGenerator.StepListener.NONE, token)).isInstanceOf(CancelledException.class);
        assertThat(llm.requests()).isEmpty();
    }
}
