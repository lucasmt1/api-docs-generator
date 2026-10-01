package dev.apidocs.core.pipeline;

import dev.apidocs.core.ai.TokenUsage;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Warning;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record PipelineResult(Path outputDir, ApiModel model, List<Warning> warnings, TokenUsage usage, int llmCalls,
        Duration duration) {

    public PipelineResult {
        warnings = List.copyOf(warnings);
    }
}
