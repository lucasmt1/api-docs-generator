package dev.apidocs.core.ai;

public record LlmResponse(String text, LlmStopReason stopReason, TokenUsage usage, String model) {
}
