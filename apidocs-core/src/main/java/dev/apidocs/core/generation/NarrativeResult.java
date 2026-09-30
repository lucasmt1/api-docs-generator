package dev.apidocs.core.generation;

import dev.apidocs.core.ai.TokenUsage;
import dev.apidocs.core.model.Warning;
import java.util.List;

public record NarrativeResult(NarrativeSet narratives, TokenUsage usage, int llmCalls, List<Warning> warnings) {

    public NarrativeResult {
        warnings = List.copyOf(warnings);
    }
}
