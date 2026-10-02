package dev.apidocs.core.generation;

import dev.apidocs.core.model.ApiModel;
import java.util.List;

/**
 * Inputs of the Final Delivery stage. {@code narrativeSource} names what wrote the narrative texts as provider and
 * model (e.g. {@code gemini / gemini-3.8-flash}, or {@code dry-run}); it is published, so it never names a host.
 */
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
