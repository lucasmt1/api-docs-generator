package dev.apidocs.core.generation;

import dev.apidocs.core.ai.narrative.ArchitectureNarrative;
import dev.apidocs.core.ai.narrative.ControllerNarrative;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import java.util.Map;
import java.util.Optional;

/** LLM-written texts, already validated. Missing parts render as placeholders. */
public record NarrativeSet(
        Map<String, ControllerNarrative> controllers,
        TechnicalDocNarrative technical,
        ArchitectureNarrative architecture) {

    public NarrativeSet {
        controllers = Map.copyOf(controllers);
    }

    public static NarrativeSet empty() {
        return new NarrativeSet(Map.of(), null, null);
    }

    public Optional<ControllerNarrative> controller(String name) {
        return Optional.ofNullable(controllers.get(name));
    }

    public Optional<EndpointNarrative> endpoint(String controllerName, String endpointId) {
        return controller(controllerName).flatMap(narrative -> narrative.endpoints().stream()
                .filter(endpoint -> endpoint.endpointId().equals(endpointId))
                .findFirst());
    }

    public Optional<TechnicalDocNarrative> technicalDoc() {
        return Optional.ofNullable(technical);
    }

    public Optional<ArchitectureNarrative> architectureDoc() {
        return Optional.ofNullable(architecture);
    }
}
