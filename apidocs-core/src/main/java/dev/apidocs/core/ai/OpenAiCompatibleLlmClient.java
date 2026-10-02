package dev.apidocs.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.support.JsonSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adapter for servers speaking the OpenAI Chat Completions protocol: OpenAI, Gemini (OpenAI compatibility),
 * Ollama, Groq, OpenRouter... Uses only the common subset of the protocol.
 */
public final class OpenAiCompatibleLlmClient implements LlmClient {

    public enum JsonMode { JSON_SCHEMA, JSON_OBJECT, PROMPT }

    /** {@code maxTokensField} is {@code max_tokens} or {@code max_completion_tokens} (OpenAI reasoning models). */
    public record Options(JsonMode jsonMode, boolean sendTemperature, String maxTokensField) {
    }

    public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, boolean jitter) {

        public static final RetryPolicy DEFAULT = new RetryPolicy(4, Duration.ofSeconds(2), Duration.ofSeconds(60), true);
    }

    private static final System.Logger LOG = System.getLogger(OpenAiCompatibleLlmClient.class.getName());
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504);
    private static final String SCHEMA_INSTRUCTION = "\n\nRespond ONLY with a JSON object that matches this JSON Schema:\n";
    /** How failure messages name the server: never by host, because they end up in the published documents. */
    private static final String PROVIDER = "the LLM provider";
    /** Keeps {@code 1L << n} and the multiplication by the base delay far from overflow. */
    private static final int MAX_BACKOFF_DOUBLINGS = 20;

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Options options;
    private final RetryPolicy retry;
    private final Sleeper sleeper;
    private final Duration requestTimeout;
    private volatile JsonMode jsonMode;

    public OpenAiCompatibleLlmClient(HttpClient http, String baseUrl, String apiKey, String model, Options options,
            RetryPolicy retry, Sleeper sleeper, Duration requestTimeout) {
        this.http = http;
        this.endpoint = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/").resolve("chat/completions");
        ApiKeys.requireHeaderSafe(apiKey);
        this.apiKey = apiKey;
        this.model = model;
        this.options = options;
        this.retry = retry;
        this.sleeper = sleeper;
        this.requestTimeout = requestTimeout;
        this.jsonMode = options.jsonMode();
    }

    public static OpenAiCompatibleLlmClient create(String baseUrl, String apiKey, String model, Options options) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new OpenAiCompatibleLlmClient(http, baseUrl, apiKey, model, options, RetryPolicy.DEFAULT, Sleeper.REAL,
                Duration.ofMinutes(5));
    }

    @Override
    public String id() {
        return endpoint.getHost() + ":" + model;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        for (int attempt = 1; ; attempt++) {
            JsonMode mode = jsonMode;
            HttpResponse<String> response;
            try {
                response = http.send(buildRequest(request, mode), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (attempt < retry.maxAttempts()) {
                    pause(delay(attempt, Optional.empty()));
                    continue;
                }
                throw new LlmException("Request to " + PROVIDER + " failed (" + e.getClass().getSimpleName() + ")",
                        detail(e.getMessage()), true, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException("Interrupted while waiting for the LLM");
            } catch (IllegalArgumentException e) {
                // Deliberately no message or cause from the JDK: for an invalid header it quotes the whole value,
                // which would put the API key into warnings and stack traces.
                throw new LlmException("Invalid request to " + PROVIDER
                        + ": the HTTP client rejected it (check the base URL and the API key)", host(), false);
            }
            int status = response.statusCode();
            if (status == 200) {
                return parse(response.body());
            }
            if (status == 400 && mode == JsonMode.JSON_SCHEMA && request.outputSchema() != null) {
                LOG.log(System.Logger.Level.DEBUG, "{0} rejected response_format json_schema; using json_object",
                        endpoint.getHost());
                jsonMode = JsonMode.JSON_OBJECT;
                continue;
            }
            boolean retryable = RETRYABLE.contains(status);
            if (retryable && attempt < retry.maxAttempts()) {
                LOG.log(System.Logger.Level.DEBUG, "HTTP {0} from {1}; retrying", status, endpoint.getHost());
                pause(delay(attempt, response.headers().firstValue("Retry-After")));
                continue;
            }
            throw new LlmException("HTTP " + status + " from " + PROVIDER, detail(response.body()), retryable);
        }
    }

    private HttpRequest buildRequest(LlmRequest request, JsonMode mode) {
        ObjectNode body = JsonSupport.mapper().createObjectNode();
        body.put("model", model);
        String system = request.systemPrompt();
        if (request.outputSchema() != null && mode != JsonMode.JSON_SCHEMA) {
            system = system + SCHEMA_INSTRUCTION + request.outputSchema();
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        if (options.sendTemperature()) {
            body.put("temperature", 0.2);
        }
        body.put(options.maxTokensField(), request.maxOutputTokens());
        if (request.outputSchema() != null) {
            switch (mode) {
                case JSON_SCHEMA -> {
                    ObjectNode format = body.putObject("response_format");
                    format.put("type", "json_schema");
                    ObjectNode jsonSchema = format.putObject("json_schema");
                    jsonSchema.put("name", "response");
                    jsonSchema.put("strict", true);
                    jsonSchema.set("schema", request.outputSchema());
                }
                case JSON_OBJECT -> body.putObject("response_format").put("type", "json_object");
                case PROMPT -> {
                }
            }
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonSupport.toJson(body), StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private LlmResponse parse(String body) {
        JsonNode root;
        try {
            root = JsonSupport.mapper().readTree(body);
        } catch (JsonProcessingException malformedJson) {
            // no cause: Jackson quotes the offending token, which may be an echoed API key
            throw new LlmException("Invalid JSON from " + PROVIDER, detail(body), false);
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (!message.isObject()) {
            throw new LlmException("Unexpected answer from " + PROVIDER + " (no message in the first choice)",
                    detail(body), false);
        }
        String finish = choice.path("finish_reason").asText("");
        boolean refused = message.hasNonNull("refusal") && !message.get("refusal").asText().isBlank()
                || finish.equals("content_filter");
        LlmStopReason stop = refused ? LlmStopReason.REFUSAL
                : finish.equals("length") ? LlmStopReason.MAX_TOKENS
                : LlmStopReason.COMPLETE;
        JsonNode usage = root.path("usage");
        TokenUsage tokens = new TokenUsage(usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0),
                usage.path("prompt_tokens_details").path("cached_tokens").asLong(0));
        return new LlmResponse(message.path("content").asText(""), stop, tokens, root.path("model").asText(model));
    }

    private Duration delay(int attempt, Optional<String> retryAfter) {
        Optional<Duration> fromHeader = retryAfter.flatMap(OpenAiCompatibleLlmClient::parseSeconds);
        Duration delay = fromHeader.orElseGet(
                () -> retry.baseDelay().multipliedBy(1L << Math.min(attempt - 1, MAX_BACKOFF_DOUBLINGS)));
        if (fromHeader.isEmpty() && retry.jitter()) {
            delay = delay.plusMillis(ThreadLocalRandom.current().nextLong(retry.baseDelay().toMillis() / 2 + 1));
        }
        return delay.compareTo(retry.maxDelay()) > 0 ? retry.maxDelay() : delay;
    }

    /** Non-negative delay in seconds from a {@code Retry-After} header; dates and garbage are ignored. */
    private static Optional<Duration> parseSeconds(String value) {
        try {
            Duration duration = Duration.ofMillis((long) (Double.parseDouble(value.strip()) * 1000));
            return duration.isNegative() ? Optional.empty() : Optional.of(duration);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException("Interrupted while waiting to retry");
        }
    }

    /**
     * Detail of a failure (console only): the endpoint's host, then the redacted text of the server or the JDK.
     * Neither goes into the message, which is persisted in the documents: providers name accounts and projects in
     * their errors, and a self-hosted endpoint's host may be internal.
     */
    private String detail(String text) {
        String redacted = ApiKeys.redact(text, apiKey);
        return redacted.isEmpty() ? host() : host() + ": " + redacted;
    }

    private String host() {
        return endpoint.getHost() == null ? "unknown host" : endpoint.getHost();
    }
}
