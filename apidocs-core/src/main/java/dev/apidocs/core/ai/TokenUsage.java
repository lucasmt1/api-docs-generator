package dev.apidocs.core.ai;

public record TokenUsage(long inputTokens, long outputTokens, long cachedInputTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                cachedInputTokens + other.cachedInputTokens);
    }
}
