package dev.apidocs.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** {@code outputSchema} is a JSON Schema the answer must follow, or {@code null} for free text. */
public record LlmRequest(String purpose, String systemPrompt, String userPrompt, JsonNode outputSchema, int maxOutputTokens) {
}
