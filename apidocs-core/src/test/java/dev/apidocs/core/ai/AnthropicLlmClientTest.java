package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.CancelledException;
import dev.apidocs.core.ai.narrative.GlossaryEntry;
import dev.apidocs.core.support.JsonSupport;
import dev.apidocs.core.testsupport.StubHttpServer;
import dev.apidocs.core.testsupport.StubHttpServer.Reply;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicLlmClientTest {

    private final ObjectNode schema = RecordSchemaGenerator.schemaFor(GlossaryEntry.class);

    private static String stream(String text, String stopReason) {
        return String.join("\n",
                "event: message_start",
                "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                        + "\"model\":\"claude-opus-5-5\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,"
                        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":1}}}",
                "",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":"
                        + JsonSupport.toJson(text) + "}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "",
                "event: message_delta",
                "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                        + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":7}}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                "", "");
    }

    private static AnthropicLlmClient client(StubHttpServer server) {
        return AnthropicLlmClient.create("test-key", server.baseUrl(), "claude-opus-5-5", "high", 0);
    }

    private LlmRequest request() {
        return new LlmRequest("purpose", "system prompt", "user prompt", schema, 500);
    }

    @Test
    void streamsAMessageWithSchemaEffortCachingAndFallbacks() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(stream("{\"term\":\"SKU\"}", "end_turn")));

            LlmResponse response = client(server).complete(request());

            assertThat(response.text()).isEqualTo("{\"term\":\"SKU\"}");
            assertThat(response.stopReason()).isEqualTo(LlmStopReason.COMPLETE);
            assertThat(response.usage().inputTokens()).isEqualTo(12);
            assertThat(response.usage().outputTokens()).isEqualTo(7);
            assertThat(response.model()).isEqualTo("claude-opus-5-5");

            StubHttpServer.Recorded recorded = server.requests().get(0);
            assertThat(recorded.path()).isEqualTo("/v1/messages?beta=true");
            assertThat(recorded.header("x-api-key")).isEqualTo("test-key");
            assertThat(recorded.header("anthropic-beta")).contains("server-side-fallback-2026-07-01");
            JsonNode body = JsonSupport.mapper().readTree(recorded.body());
            assertThat(body.get("model").asText()).isEqualTo("claude-opus-5-5");
            assertThat(body.get("max_tokens").asLong()).isEqualTo(64_000L);
            assertThat(body.get("fallbacks").asText()).isEqualTo("default");
            assertThat(body.get("stream").asBoolean()).isTrue();
            assertThat(body.has("temperature")).isFalse();
            assertThat(body.get("output_config").get("effort").asText()).isEqualTo("high");
            assertThat(body.get("output_config").get("format").get("type").asText()).isEqualTo("json_schema");
            assertThat(body.get("output_config").get("format").get("schema")).isEqualTo(schema);
            assertThat(body.get("system").get(0).get("text").asText()).isEqualTo("system prompt");
            assertThat(body.get("system").get(0).get("cache_control").get("type").asText()).isEqualTo("ephemeral");
            assertThat(body.get("messages").get(0).get("content").asText()).isEqualTo("user prompt");
        }
    }

    @Test
    void mapsRefusalsAndTruncation() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(stream("", "refusal"))).enqueue(Reply.sse(stream("{\"t\":", "max_tokens")));
            AnthropicLlmClient client = client(server);

            assertThat(client.complete(request()).stopReason()).isEqualTo(LlmStopReason.REFUSAL);
            assertThat(client.complete(request()).stopReason()).isEqualTo(LlmStopReason.MAX_TOKENS);
        }
    }

    @Test
    void translatesApiErrors() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(429, "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}"));
            server.enqueue(Reply.json(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}"));
            AnthropicLlmClient client = client(server);

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue());
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
                    .hasMessageContaining("HTTP 400");
        }
    }

    @Test
    void identifiesProviderAndModel() {
        try (StubHttpServer server = new StubHttpServer()) {
            assertThat(client(server).id()).isEqualTo("anthropic:claude-opus-5-5");
        }
    }

    // --- response handling ---

    @Test
    void mapsAFullContextWindowToTruncation() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(stream("{\"t\":", "model_context_window_exceeded")));

            assertThat(client(server).complete(request()).stopReason()).isEqualTo(LlmStopReason.MAX_TOKENS);
        }
    }

    @Test
    void returnsOnlyTheTextBlocksAndIgnoresThinking() {
        String thinking = String.join("\n",
                "event: content_block_start",
                "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\","
                        + "\"thinking\":\"\",\"signature\":\"\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\","
                        + "\"thinking\":\"let me think\"}}",
                "",
                "event: content_block_delta",
                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\","
                        + "\"signature\":\"sig\"}}",
                "",
                "event: content_block_stop",
                "data: {\"type\":\"content_block_stop\",\"index\":0}",
                "", "");
        String body = stream("{\"term\":\"SKU\"}", "end_turn")
                .replace("\"index\":0", "\"index\":1")
                .replace("event: content_block_start", thinking + "event: content_block_start");
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(body));

            assertThat(client(server).complete(request()).text()).isEqualTo("{\"term\":\"SKU\"}");
        }
    }

    @Test
    void reportsAStreamWithoutUsageAsAnLlmExceptionInsteadOfLeakingTheSdkException() {
        String withoutUsage = stream("{\"a\":1}", "end_turn")
                .replace(",\"usage\":{\"input_tokens\":12,\"output_tokens\":1}", "");
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(withoutUsage));

            assertThatThrownBy(() -> client(server).complete(request()))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("usage");
        }
    }

    @Test
    void sendsNoCredentialsWhenTheApiKeyIsBlankOrNull() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(stream("{}", "end_turn"))).enqueue(Reply.sse(stream("{}", "end_turn")));

            AnthropicLlmClient.create("  ", server.baseUrl(), "claude-opus-5-5", "high", 0).complete(request());
            AnthropicLlmClient.create(null, server.baseUrl(), "claude-opus-5-5", "high", 0).complete(request());

            assertThat(server.requests()).hasSize(2)
                    .allSatisfy(recorded -> assertThat(recorded.header("x-api-key")).isNull());
        }
    }

    // --- failures must always surface as LlmException ---

    @Test
    void reportsMidStreamErrorEventsByTheirErrorTypeInsteadOfTheMisleadingHttpStatus() {
        String overloaded = "event: error\n"
                + "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n";
        String invalid = "event: error\n"
                + "data: {\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}\n\n";
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(overloaded)).enqueue(Reply.sse(invalid));
            AnthropicLlmClient client = client(server);

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
                    .hasMessageContaining("overloaded_error")
                    .hasMessageNotContaining("HTTP 200");
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
                    .hasMessageContaining("invalid_request_error");
        }
    }

    @Test
    void reportsATruncatedStreamAsARetryableLlmException() {
        String full = stream("{\"t\":", "end_turn");
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(full.substring(0, full.indexOf("event: content_block_stop"))));

            assertThatThrownBy(() -> client(server).complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
                    .hasMessageContaining("Incomplete response stream");
        }
    }

    @Test
    void reportsAnAnswerThatIsNotAStreamAsAnLlmException() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(200, "{\"id\":\"msg_1\",\"type\":\"message\"}"));

            assertThatThrownBy(() -> client(server).complete(request())).isInstanceOf(LlmException.class);
        }
    }

    @Test
    void reportsConnectionFailuresAsRetryableLlmException() {
        AnthropicLlmClient client;
        try (StubHttpServer server = new StubHttpServer()) {
            client = client(server);
        }

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue())
                .hasMessageContaining("failed");
    }

    @Test
    void classifiesTransientStatusesAsRetryableAndKeepsErrorMessagesShort() {
        String page = "<html>" + "x".repeat(5_000) + "</html>";
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(408, "{\"type\":\"error\",\"error\":{\"type\":\"timeout_error\",\"message\":\"t\"}}"))
                    .enqueue(Reply.json(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"o\"}}"))
                    .enqueue(Reply.json(502, page))
                    .enqueue(Reply.json(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"no\"}}"));
            AnthropicLlmClient client = client(server);

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue());
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isTrue());
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> {
                        assertThat(e.retryable()).isTrue();
                        assertThat(e.getMessage()).hasSizeLessThan(400);
                    })
                    .hasMessageContaining("HTTP 502");
            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.retryable()).isFalse())
                    .hasMessageContaining("HTTP 401");
        }
    }

    // --- interrupts are cancellation, never a provider failure ---

    @Test
    void reportsAnAlreadyInterruptedThreadAsCancellationAndKeepsTheInterruptFlag() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.sse(stream("{}", "end_turn")));
            AnthropicLlmClient client = client(server);

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

    @Test
    void reportsAnInterruptDuringTheSdkRetryBackoffAsCancellationAndKeepsTheInterruptFlag() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            for (int i = 0; i < 4; i++) {
                server.enqueue(Reply.json(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")
                        .withHeader("retry-after-ms", "30000"));
            }
            AnthropicLlmClient client = AnthropicLlmClient.create("test-key", server.baseUrl(), "claude-opus-5-5", "high", 3);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<Boolean> interruptedAfter = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    client.complete(request());
                } catch (Throwable e) {
                    failure.set(e);
                }
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            });
            worker.start();
            while (server.requests().isEmpty()) {
                Thread.sleep(5);
            }
            worker.interrupt();
            worker.join(20_000);

            assertThat(worker.isAlive()).isFalse();
            assertThat(failure.get()).isInstanceOf(CancelledException.class).hasMessageContaining("Interrupted");
            assertThat(interruptedAfter.get()).isTrue();
        }
    }

    // --- API keys must never be echoed in errors ---

    @ParameterizedTest
    @ValueSource(strings = {"sk-SECRET\n", "sk-SECRET\r\n", "sk-SECRET\r", "sk-SE\u0000CRET", "sk-SECRET’"})
    void rejectsApiKeysWithControlOrNonAsciiCharactersWithoutEchoingThem(String apiKey) {
        try (StubHttpServer server = new StubHttpServer()) {
            assertThatThrownBy(() -> AnthropicLlmClient.create(apiKey, server.baseUrl(), "claude-opus-5-5", "high", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("API key")
                    .satisfies(e -> assertThat(chainOf(e)).doesNotContain("sk-SECRET").doesNotContain("sk-SE"));
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void neverEchoesTheApiKeyWhenTheSdkClientRejectsTheHeader() {
        String secret = "sk-SECRET";
        try (StubHttpServer server = new StubHttpServer()) {
            // built by hand: the public constructor cannot vet the key buried in a ready-made SDK client
            AnthropicLlmClient client = new AnthropicLlmClient(AnthropicOkHttpClient.builder()
                    .apiKey(secret + "\n").baseUrl(server.baseUrl()).maxRetries(0).build(), "claude-opus-5-5", "high");

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> {
                        assertThat(e.retryable()).isFalse();
                        assertThat(e.getCause()).isNull();
                        assertThat(e.getSuppressed()).isEmpty();
                        assertThat(chainOf(e)).doesNotContain(secret);
                    });
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void neverPutsTheApiKeyIntoApiErrors() {
        try (StubHttpServer server = new StubHttpServer()) {
            server.enqueue(Reply.json(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
                    + "\"message\":\"invalid x-api-key\"}}"));

            assertThatThrownBy(() -> AnthropicLlmClient.create("sk-SECRET", server.baseUrl(), "claude-opus-5-5", "high", 0)
                    .complete(request()))
                    .isInstanceOfSatisfying(LlmException.class, e -> assertThat(chainOf(e)).doesNotContain("sk-SECRET"));
        }
    }

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
}
