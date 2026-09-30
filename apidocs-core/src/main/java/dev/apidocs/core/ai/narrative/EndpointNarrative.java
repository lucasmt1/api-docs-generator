package dev.apidocs.core.ai.narrative;

import dev.apidocs.core.ai.Describe;
import java.util.List;

public record EndpointNarrative(
        @Describe("Exactly one of the endpoint ids listed in the input.") String endpointId,
        @Describe("Short action title, for example 'Create order'.") String title,
        @Describe("One sentence.") String summary,
        @Describe("One short paragraph: what the endpoint does and its effects.") String description,
        @Describe("Business rules enforced by this endpoint, one per item; empty list if none.") List<String> businessRules,
        @Describe("When each listed error status happens; only statuses listed for the endpoint.") List<ErrorScenario> errorScenarios,
        @Describe("Example request body as a JSON string using only schema fields; empty string when there is no body.") String requestExample,
        @Describe("Example success response body as a JSON string using only schema fields; empty string when there is no body.") String responseExample) {

    public EndpointNarrative withExamples(String request, String response) {
        return new EndpointNarrative(endpointId, title, summary, description, businessRules, errorScenarios, request, response);
    }
}
