package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.ai.OpenAiCompatibleLlmClient.JsonMode;
import dev.apidocs.core.ai.OpenAiCompatibleLlmClient.Options;
import dev.apidocs.core.ai.OpenAiCompatibleLlmClient.RetryPolicy;
import dev.apidocs.core.ai.narrative.GlossaryEntry;
import dev.apidocs.core.support.JsonSupport;
import dev.apidocs.core.testsupport.StubHttpServer;
import dev.apidocs.core.testsupport.StubHttpServer.Reply;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiCompatibleLlmClientTest {

    private static final String OK = """
            {"id":"x","object":"chat.completion","model":"test-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"a\\":1}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":4}}}
            """;

    private final List<Duration> sleeps = new ArrayList<>();
    private final ObjectNode schema = RecordSchemaGenerator.schemaFor(GlossaryEntry.class);

    private OpenAiCompatibleLlmClient client(StubHttpServer server, String apiKey, Options options) {
        return client(server, apiKey, options, sleeps::add);
    }

    private OpenAiCompatibleLlmClient client(StubHttpServer server, String apiKey, Options options, Sleeper sleeper) {
        return new OpenAiCompatibleLlmClient(HttpClient.newHttpClient(), server.baseUrl() + "/v1", apiKey, "test-model",
                options, new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(60), false), sleeper,
                Duration.ofSeconds(10));
    }

    private LlmRequest request() {
        return new LlmRequest("purpose", "system prompt", "user prompt", schema, 500);
    }

    private static String finishing(String reason) {
        return OK.replace("\"finish_reason\":\"stop\"", "\"finish_reason\":\"" + reason + "\"");
    }

    @Test
    void sendsAChatCompletionWithJsonSchemaAndParsesTheAnswer() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, OK));

            LlmResponse response = client(server, "secret", new Options(JsonMode.JSON_SCHEMA, true, "max_tokens"))
                    .complete(request());

            assertThat(response.text()).isEqualTo("{\"a\":1}");
            assertThat(response.stopReason()).isEqualTo(LlmStopReason.COMPLETE);
            assertThat(response.usage()).isEqualTo(new TokenUsage(12, 5, 4));
            assertThat(response.model()).isEqualTo("test-model");
            StubHttpServer.Recorded recorded = server.requests().get(0);
            assertThat(recorded.method()).isEqualTo("POST");
            assertThat(recorded.path()).isEqualTo("/v1/chat/completions");
            assertThat(recorded.header("Authorization")).isEqualTo("Bearer secret");
            JsonNode body = JsonSupport.mapper().readTree(recorded.body());
            assertThat(body.get("model").asText()).isEqualTo("test-model");
            assertThat(body.get("messages").get(0).get("content").asText()).isEqualTo("system prompt");
            assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("user prompt");
            assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.get("max_tokens").asInt()).isEqualTo(500);
            assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_schema");
            assertThat(body.get("response_format").get("json_schema").get("schema")).isEqualTo(schema);
        }
    }

    @Test
    void supportsJsonObjectModeAndReasoningModelParameters() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, OK));

            client(server, null, new Options(JsonMode.JSON_OBJECT, false, "max_completion_tokens")).complete(request());

            StubHttpServer.Recorded recorded = server.requests().get(0);
            assertThat(recorded.header("Authorization")).isNull();
            JsonNode body = JsonSupport.mapper().readTree(recorded.body());
            assertThat(body.get("messages").get(0).get("content").asText())
                    .startsWith("system prompt")
                    .contains("Respond ONLY with a JSON object that matches this JSON Schema");
            assertThat(body.has("temperature")).isFalse();
            assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(500);
            assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_object");
        }
    }

    @Test
    void fallsBackToJsonObjectWhenTheServerRejectsJsonSchema() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(400, "{\"error\":\"response_format not supported\"}"));
            server.enqueue(Reply.json(200, OK));

            LlmResponse response = client(server, "k", new Options(JsonMode.JSON_SCHEMA, true, "max_tokens"))
                    .complete(request());

            assertThat(response.text()).isEqualTo("{\"a\":1}");
            JsonNode second = JsonSupport.mapper().readTree(server.requests().get(1).body());
            assertThat(second.get("response_format").get("type").asText()).isEqualTo("json_object");
            assertThat(sleeps).isEmpty();
        }
    }

    @Test
    void retriesRateLimitsHonoringRetryAfter() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(429, "{}").withHeader("Retry-After", "1"));
            server.enqueue(Reply.json(200, OK));

            client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens")).complete(request());

            assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
            assertThat(server.requests()).hasSize(2);
        }
    }

    @Test
    void givesUpAfterMaxAttemptsOnServerErrors() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(500, "boom")).enqueue(Reply.json(500, "boom")).enqueue(Reply.json(500, "boom"));

            assertThatThrownBy(() -> client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"))
                    .complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
                    .hasMessageContaining("HTTP 500");
            assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void doesNotRetryClientErrors() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(401, "{\"error\":\"bad key\"}"));

            assertThatThrownBy(() -> client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"))
                    .complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
                    .hasMessageContaining("HTTP 401");
            assertThat(server.requests()).hasSize(1);
            assertThat(sleeps).isEmpty();
        }
    }

    @Test
    void masksTheApiKeyInServerTextCopiedIntoErrors() {
        String key = "AIzaSyFAKE0123456789abcdef";
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(401, "{\"error\":\"invalid header Authorization: Bearer " + key + "\"}"))
                    .enqueue(Reply.json(200, key + " is not a valid key"))
                    .enqueue(Reply.json(200, "{\"choices\":[],\"echo\":\"" + key + "\"}"));
            OpenAiCompatibleLlmClient client = client(server, key, new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));

            for (String expected : List.of("HTTP 401", "Invalid JSON", "no message")) {
                assertThatThrownBy(() -> client.complete(request()))
                        .isInstanceOfSatisfying(LlmException.class, e -> {
                            assertThat(e.detail()).contains("***").doesNotContain(key);
                            assertThat(chainOf(e)).doesNotContain(key);
                        })
                        .hasMessageContaining(expected);
            }
        }
    }

    @Test
    void keepsServerTextOutOfTheMessageAndPutsItRedactedIntoTheDetail() {
        String key = "sk-proj-AbCdEfGhIjKlMnOpQrSt";
        String body = "{\"error\":{\"message\":\"Key sk-proj-AbCd... of organization org-AbC123 has no access to "
                + "project 'projects/123456789'\"}}";
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(403, body))
                    .enqueue(Reply.json(200, "Not JSON: " + body))
                    .enqueue(Reply.json(200, "{\"choices\":[]," + body.substring(1)));
            OpenAiCompatibleLlmClient client = client(server, key, new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));

            for (String summary : List.of("HTTP 403 from the LLM provider", "Invalid JSON from the LLM provider",
                    "Unexpected answer from the LLM provider (no message in the first choice)")) {
                assertThatThrownBy(() -> client.complete(request()))
                        .isInstanceOfSatisfying(LlmException.class, e -> {
                            assertThat(e.getMessage()).isEqualTo(summary);
                            assertThat(e.detail()).startsWith("127.0.0.1: ")
                                    .contains("org-AbC123", "projects/123456789", "Key ***...")
                                    .doesNotContain("sk-proj-AbCd");
                            assertThat(chainOf(e)).doesNotContain("org-AbC123").doesNotContain("127.0.0.1");
                        });
            }
        }
    }

    @Test
    void mapsFinishReasons() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, finishing("length"))).enqueue(Reply.json(200, finishing("content_filter")));
            OpenAiCompatibleLlmClient client = client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));

            assertThat(client.complete(request()).stopReason()).isEqualTo(LlmStopReason.MAX_TOKENS);
            assertThat(client.complete(request()).stopReason()).isEqualTo(LlmStopReason.REFUSAL);
        }
    }

    // --- LlmException contract: every failure surfaces as LlmException, usage is never null ---

    @Test
    void reportsMalformedJsonAsAnLlmException() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, "{not json"));

            assertThatThrownBy(() -> client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"))
                    .complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
                    .hasMessageContaining("Invalid JSON");
        }
    }

    @Test
    void reportsAnAnswerWithoutChoicesAsAnLlmException() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, "{\"id\":\"x\",\"choices\":[]}")).enqueue(Reply.json(200, ""));
            OpenAiCompatibleLlmClient client = client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("no message");
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("no message");
        }
    }

    @Test
    void usesZeroUsageWhenTheProviderOmitsIt() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, """
                    {"choices":[{"message":{"role":"assistant","content":"{}"},"finish_reason":"stop"}]}
                    """));

            LlmResponse response = client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"))
                    .complete(request());

            assertThat(response.usage()).isEqualTo(TokenUsage.ZERO);
            assertThat(response.model()).isEqualTo("test-model");
        }
    }

    @Test
    void reportsConnectionFailuresAsRetryableLlmExceptionAfterRetrying() {
        OpenAiCompatibleLlmClient client;
        try (StubHttpServer server = new StubHttpServer()) {
            client = client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));
        }

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e).hasCauseInstanceOf(IOException.class);
                    assertThat(e.getMessage()).isEqualTo("Request to the LLM provider failed ("
                            + e.getCause().getClass().getSimpleName() + ")").doesNotContain("127.0.0.1");
                    assertThat(e.detail()).startsWith("127.0.0.1");
                });
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void ignoresNegativeRetryAfterValues() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(429, "{}").withHeader("Retry-After", "-5"));
            server.enqueue(Reply.json(200, OK));

            client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens")).complete(request());

            assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        }
    }

    @Test
    void reportsAnInterruptedBackoffAsCancellationAndKeepsTheInterruptFlag() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(500, "boom"));
            Sleeper interrupted = duration -> {
                throw new InterruptedException("stop");
            };

            try {
                assertThatThrownBy(() -> client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"),
                        interrupted).complete(request()))
                        .isInstanceOf(CancelledException.class)
                        .hasMessageContaining("Interrupted");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void reportsAnInterruptedRequestAsCancellationAndKeepsTheInterruptFlag() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, OK));
            OpenAiCompatibleLlmClient client = client(server, "k", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"));

            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> client.complete(request()))
                        .isInstanceOf(CancelledException.class)
                        .hasMessageContaining("Interrupted");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }
    }

    // --- API keys must never be echoed in errors ---

    @ParameterizedTest
    @ValueSource(strings = {"sk-SECRET\n", "sk-SECRET\r\n", "sk-SECRET\r", "sk-SE\u0000CRET", "sk-SECRET’"})
    void rejectsApiKeysWithControlOrNonAsciiCharactersWithoutEchoingThem(String apiKey) {
        try (StubHttpServer server = new StubHttpServer()) {
            assertThatThrownBy(() -> client(server, apiKey, new Options(JsonMode.JSON_OBJECT, true, "max_tokens")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("API key")
                    .satisfies(e -> assertThat(chainOf(e)).doesNotContain("sk-SECRET").doesNotContain("sk-SE"));
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void acceptsBlankAndOrdinaryApiKeys() {
        try (StubHttpServer server = new StubHttpServer()) {
            Options options = new Options(JsonMode.JSON_OBJECT, true, "max_tokens");

            assertThat(client(server, "sk-abc_DEF.123~-+/=", options)).isNotNull();
            assertThat(client(server, "", options)).isNotNull();
            assertThat(client(server, "  ", options)).isNotNull();
        }
    }

    @Test
    void neverEchoesTheApiKeyWhenTheHttpClientRejectsTheRequest() {
        String secret = "sk-SECRET";
        HttpClient rejecting = new RejectingHttpClient(
                new IllegalArgumentException("invalid header value: \"Bearer " + secret + "\"",
                        new IllegalArgumentException("cause also has " + secret)));
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(rejecting, "http://127.0.0.1:1/v1", secret,
                "test-model", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"),
                new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(60), false), sleeps::add, Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.getCause()).isNull();
                    assertThat(e.getSuppressed()).isEmpty();
                    assertThat(chainOf(e)).doesNotContain(secret);
                });
        assertThat(sleeps).isEmpty();
    }

    @Test
    void reportsAnUnsupportedUrlSchemeAsAnLlmExceptionWithoutRequestingAnything() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(HttpClient.newHttpClient(),
                "ftp://127.0.0.1:1/v1", "k", "test-model", new Options(JsonMode.JSON_OBJECT, true, "max_tokens"),
                new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(60), false), sleeps::add, Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.retryable()).isFalse();
                    assertThat(e.getCause()).isNull();
                    assertThat(e.detail()).isEqualTo("127.0.0.1");
                })
                .hasMessageContaining("Invalid request to the LLM provider")
                .hasMessageNotContaining("127.0.0.1");
        assertThat(sleeps).isEmpty();
    }

    /** Text of a throwable and everything attached to it (message, cause chain, suppressed), for leak checks. */
    private static String chainOf(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            text.append(t).append('\n');
            for (Throwable suppressed : t.getSuppressed()) {
                text.append(chainOf(suppressed));
            }
        }
        return text.toString();
    }

    /** {@link HttpClient} whose {@code send} always fails with the given exception; nothing else is used. */
    private static final class RejectingHttpClient extends HttpClient {

        private final RuntimeException failure;

        RejectingHttpClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw failure;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLParameters sslParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }
}
