package dev.apidocs.core.ai.narrative;

import dev.apidocs.core.ai.Describe;
import java.util.List;

public record ArchitectureNarrative(
        @Describe("One paragraph: architecture style and how a request flows through the layers.") String summary,
        @Describe("One entry per layer present in the project.") List<LayerDescription> layers,
        @Describe("Design patterns and conventions visible in the code.") List<String> patterns,
        @Describe("One entry per alert listed in the input, with the same code and subject.") List<AlertComment> alertComments,
        @Describe("Up to five prioritized improvement suggestions.") List<String> recommendations) {
}
