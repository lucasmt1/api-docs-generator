package dev.apidocs.core.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.SseException;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.ErrorType;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.support.JsonSupport;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Claude adapter built on the official Anthropic Java SDK (streaming, structured output, prompt caching).
 *
 * <p>Every failure of the SDK surfaces as an {@link LlmException}, except interrupts, which surface as a
 * {@link CancelledException} with the interrupt flag restored. Its message is a fixed summary; what the API or the
 * SDK said goes, redacted, into its detail. The SDK exception is never attached as the cause: its message quotes the
 * raw response body, which may echo the API key.
 */
public final class AnthropicLlmClient implements LlmClient {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    /**
     * Fixed output budget, deliberately independent of {@link LlmRequest#maxOutputTokens()}: adaptive thinking at
     * high effort draws from the same budget as the visible answer, so a small per-request limit would starve the
     * JSON answer. A large value is safe because the call always streams, which avoids the HTTP timeouts of long
     * non-streaming requests.
     */
    static final long MAX_TOKENS = 64_000L;

    private static final List<ErrorType> TRANSIENT_ERRORS = List.of(ErrorType.OVERLOADED_ERROR, ErrorType.API_ERROR,
            ErrorType.RATE_LIMIT_ERROR, ErrorType.TIMEOUT_ERROR);
    private static final int MAX_CAUSE_DEPTH = 16;
    private static final Pattern ERROR_TYPE = Pattern.compile("[a-z_]{1,64}");

    private final AnthropicClient client;
    private final String model;
    private final BetaOutputConfig.Effort effort;
    /** Only used to mask echoes of the key in error details. */
    private final String apiKey;

    public AnthropicLlmClient(AnthropicClient client, String model, String effort) {
        this(client, model, effort, null);
    }

    /** {@code apiKey} is the key {@code client} sends; it is only used to mask echoes of it in error details. */
    public AnthropicLlmClient(AnthropicClient client, String model, String effort, String apiKey) {
        this.client = client;
        this.model = model;
        this.effort = BetaOutputConfig.Effort.of(effort.toLowerCase(Locale.ROOT));
        this.apiKey = apiKey;
    }

    /**
     * {@code maxRetries} is handled by the SDK (429 and 5xx with backoff honoring retry-after). A blank
     * {@code apiKey} sends no credentials, which only makes sense against a gateway that adds them.
     */
    public static AnthropicLlmClient create(String apiKey, String baseUrl, String model, String effort, int maxRetries) {
        ApiKeys.requireHeaderSafe(apiKey);
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .maxRetries(maxRetries)
                .timeout(Duration.ofMinutes(10));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.apiKey(apiKey);
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return new AnthropicLlmClient(builder.build(), model, effort, apiKey);
    }

    @Override
    public String id() {
        return "anthropic:" + model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        BetaOutputConfig.Builder output = BetaOutputConfig.builder().effort(effort);
        if (request.outputSchema() != null) {
            output.format(BetaJsonOutputFormat.builder().schema(schema(request.outputSchema())).build());
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault()
                .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder()
                        .text(request.systemPrompt())
                        .cacheControl(BetaCacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(request.userPrompt())
                .outputConfig(output.build())
                .build();

        try {
            return read(stream(params));
        } catch (Exception e) {
            // Exception, not RuntimeException: the SDK is Kotlin, so its retry back-off rethrows a checked
            // InterruptedException that no Java signature declares.
            throw translate(e);
        }
    }

    private BetaMessage stream(MessageCreateParams params) {
        BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
        try (StreamResponse<BetaRawMessageStreamEvent> stream = client.beta().messages().createStreaming(params)) {
            stream.stream().forEach(accumulator::accumulate);
            return accumulator.message();
        }
    }

    private static LlmResponse read(BetaMessage message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining());
        TokenUsage usage = new TokenUsage(message.usage().inputTokens(), message.usage().outputTokens(),
                message.usage().cacheReadInputTokens().orElse(0L));
        return new LlmResponse(text, stopReason(message), usage, message.model().asString());
    }

    private static LlmStopReason stopReason(BetaMessage message) {
        return message.stopReason().map(reason -> {
            if (reason.equals(BetaStopReason.REFUSAL)) {
                return LlmStopReason.REFUSAL;
            }
            if (reason.equals(BetaStopReason.MAX_TOKENS) || reason.equals(BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED)) {
                return LlmStopReason.MAX_TOKENS;
            }
            return LlmStopReason.COMPLETE;
        }).orElse(LlmStopReason.COMPLETE);
    }

    /** Maps whatever the SDK threw to the port's exceptions; interrupts win over everything else. */
    private RuntimeException translate(Exception e) {
        if (isInterrupt(e)) {
            Thread.currentThread().interrupt();
            return new CancelledException("Interrupted while waiting for the LLM");
        }
        return switch (e) {
            case RateLimitException rateLimit -> new LlmException(
                    "Anthropic rate limit still exceeded after retries (HTTP 429)", detail(rateLimit), true);
            // An error event inside a 200 stream: the status code says nothing, the error type does.
            case SseException sse -> new LlmException("Anthropic stream error (" + errorType(sse) + ")", detail(sse),
                    sse.errorType().map(TRANSIENT_ERRORS::contains).orElse(false));
            case AnthropicServiceException service -> new LlmException(
                    "Anthropic API error (HTTP " + service.statusCode() + ")", detail(service),
                    isTransientStatus(service.statusCode()));
            case AnthropicException sdk -> new LlmException(
                    "Anthropic request failed (" + sdk.getClass().getSimpleName() + ")", detail(sdk), true);
            case IllegalStateException state ->
                    new LlmException("Incomplete response stream from Anthropic", detail(state), true);
            // Deliberately no detail or cause: a failure outside the SDK's own hierarchy (the HTTP client rejecting a
            // header, say) may quote request headers, which would put the API key into logs and stack traces.
            default -> new LlmException("Unexpected failure calling Anthropic (" + e.getClass().getSimpleName() + ")",
                    false);
        };
    }

    private String detail(Exception e) {
        return detail(e, apiKey);
    }

    /**
     * The failure's message and those of its causes, each redacted: for I/O failures the SDK only says "Request
     * failed", and the reason (refused connection, timeout, TLS...) is in the causes, which are not attached.
     */
    static String detail(Throwable failure, String apiKey) {
        StringBuilder detail = new StringBuilder(ApiKeys.redact(failure.getMessage(), apiKey));
        Throwable cause = failure.getCause();
        for (int depth = 0; cause != null && cause != failure && depth < MAX_CAUSE_DEPTH; depth++) {
            String message = ApiKeys.redact(cause.getMessage(), apiKey);
            detail.append("; caused by ").append(cause.getClass().getSimpleName())
                    .append(message.isEmpty() ? "" : ": " + message);
            cause = cause.getCause();
        }
        return detail.toString();
    }

    /** The statuses the SDK itself retries: timeouts, conflicts and server errors (429 has its own exception). */
    private static boolean isTransientStatus(int statusCode) {
        return statusCode == 408 || statusCode == 409 || statusCode >= 500;
    }

    /** The API's error type, if it is one (a gateway could put any text there, and messages hold no server text). */
    private static String errorType(AnthropicServiceException exception) {
        return exception.errorType().map(ErrorType::asString).filter(type -> ERROR_TYPE.matcher(type).matches())
                .orElse("unknown");
    }

    /** True when the thread was interrupted, however the SDK chose to report it. */
    private static boolean isInterrupt(Throwable failure) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static BetaJsonOutputFormat.Schema schema(JsonNode schema) {
        Map<String, Object> values = JsonSupport.mapper()
                .convertValue(schema, new TypeReference<LinkedHashMap<String, Object>>() { });
        BetaJsonOutputFormat.Schema.Builder builder = BetaJsonOutputFormat.Schema.builder();
        values.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }
}
