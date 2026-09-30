package dev.apidocs.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.apidocs.core.support.JsonSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Calls nothing: records the prompts so they can be inspected (written to {@code prompts/} by the pipeline). */
public final class DryRunLlmClient implements LlmClient {

    public record RecordedPrompt(int sequence, String purpose, String systemPrompt, String userPrompt, JsonNode outputSchema) {

        public String fileName() {
            return String.format(Locale.ROOT, "%02d-%s.md", sequence, purpose.replaceAll("[^A-Za-z0-9._-]", "_"));
        }

        public String toMarkdown() {
            StringBuilder out = new StringBuilder()
                    .append("# ").append(purpose).append("\n\n")
                    .append("## System\n\n````text\n").append(systemPrompt.strip()).append("\n````\n\n")
                    .append("## User\n\n````text\n").append(userPrompt.strip()).append("\n````\n");
            if (outputSchema != null) {
                out.append("\n## Output schema\n\n```json\n").append(JsonSupport.toPrettyJson(outputSchema).strip())
                        .append("\n```\n");
            }
            return out.toString();
        }
    }

    private final List<RecordedPrompt> prompts = new ArrayList<>();

    @Override
    public String id() {
        return "dry-run";
    }

    @Override
    public synchronized LlmResponse complete(LlmRequest request) {
        prompts.add(new RecordedPrompt(prompts.size() + 1, request.purpose(), request.systemPrompt(),
                request.userPrompt(), request.outputSchema()));
        return new LlmResponse("", LlmStopReason.DRY_RUN, TokenUsage.ZERO, "dry-run");
    }

    public synchronized List<RecordedPrompt> recordedPrompts() {
        return List.copyOf(prompts);
    }
}
