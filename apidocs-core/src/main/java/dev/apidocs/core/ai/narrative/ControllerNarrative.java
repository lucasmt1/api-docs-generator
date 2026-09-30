package dev.apidocs.core.ai.narrative;

import dev.apidocs.core.ai.Describe;
import java.util.List;

public record ControllerNarrative(
        @Describe("Two or three sentences describing what this controller manages.") String controllerSummary,
        @Describe("One entry for each endpoint id listed in the input.") List<EndpointNarrative> endpoints) {
}
