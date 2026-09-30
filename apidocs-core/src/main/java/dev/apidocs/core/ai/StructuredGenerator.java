package dev.apidocs.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.support.JsonSupport;
import java.util.List;
import java.util.Optional;

/** Asks the LLM for a JSON object matching a record, validates it and retries once with the problem explained. */
public final class StructuredGenerator {

    static final String RETRY_NOTE = "\n\nIMPORTANT: your previous answer could not be used (%s). "
            + "Reply again with ONLY one JSON object that matches the required schema.";

    public record Result<T>(Optional<T> value, TokenUsage usage, int calls, List<Warning> warnings) {
    }

    private final LlmClient client;

    public StructuredGenerator(LlmClient client) {
        this.client = client;
    }

    public <T extends Record> Result<T> generate(String purpose, String systemPrompt, String userPrompt, Class<T> type,
            int maxOutputTokens) {
        ObjectNode schema = RecordSchemaGenerator.schemaFor(type);
        TokenUsage usage = TokenUsage.ZERO;
        int calls = 0;
        String prompt = userPrompt;
        String problem = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            LlmResponse response;
            try {
                response = client.complete(new LlmRequest(purpose, systemPrompt, prompt, schema, maxOutputTokens));
            } catch (LlmException e) {
                return new Result<>(Optional.empty(), usage, calls,
                        List.of(new Warning("LLM_CALL_FAILED", e.getMessage(), purpose)));
            }
            calls++;
            usage = usage.plus(response.usage());
            if (response.stopReason() == LlmStopReason.DRY_RUN) {
                return new Result<>(Optional.empty(), usage, calls, List.of());
            }
            if (response.stopReason() == LlmStopReason.REFUSAL) {
                return new Result<>(Optional.empty(), usage, calls,
                        List.of(new Warning("LLM_REFUSED", "The model declined to answer", purpose)));
            }
            try {
                T value = JsonSupport.mapper().readValue(extractJson(response.text()), type);
                List<String> missing = RecordValidator.missingFields(value);
                if (missing.isEmpty()) {
                    return new Result<>(Optional.of(value), usage, calls, List.of());
                }
                problem = "missing fields " + missing;
            } catch (JsonProcessingException | IllegalArgumentException e) {
                problem = "invalid JSON: " + firstLine(e.getMessage());
            }
            if (response.stopReason() == LlmStopReason.MAX_TOKENS) {
                problem = "the answer was cut off by the output limit, be more concise; " + problem;
            }
            prompt = userPrompt + RETRY_NOTE.formatted(problem);
        }
        return new Result<>(Optional.empty(), usage, calls, List.of(new Warning("LLM_OUTPUT_INVALID", problem, purpose)));
    }

    /** The outermost JSON object in the text (tolerates Markdown fences and chatter around it). */
    static String extractJson(String text) {
        String trimmed = text == null ? "" : text.strip();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("no JSON object found");
        }
        return trimmed.substring(start, end + 1);
    }

    private static String firstLine(String message) {
        String line = message == null ? "" : message.lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) : line;
    }
}
