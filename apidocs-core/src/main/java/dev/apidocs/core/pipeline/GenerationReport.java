package dev.apidocs.core.pipeline;

import dev.apidocs.core.ai.TokenUsage;
import dev.apidocs.core.model.Warning;
import java.util.List;

/** {@code generation-report.json}: the only output that contains a timestamp. */
public record GenerationReport(
        String toolVersion,
        String provider,
        String model,
        String language,
        String generatedAt,
        long durationMillis,
        int llmCalls,
        TokenUsage usage,
        List<Warning> warnings) {
}
