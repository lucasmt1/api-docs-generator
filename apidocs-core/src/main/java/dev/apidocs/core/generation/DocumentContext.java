package dev.apidocs.core.generation;

import dev.apidocs.core.model.ApiModel;
import java.util.List;

/** Inputs of the Final Delivery stage. {@code narrativeSource} is the LLM id (e.g. {@code dry-run}). */
public record DocumentContext(
        ApiModel model,
        NarrativeSet narratives,
        List<ArchitectureAlert> alerts,
        Messages messages,
        String narrativeSource) {

    public DocumentContext {
        alerts = List.copyOf(alerts);
    }
}
