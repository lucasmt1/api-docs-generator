package dev.apidocs.core.testsupport;

import dev.apidocs.core.ai.LlmClient;
import dev.apidocs.core.ai.LlmRequest;
import dev.apidocs.core.ai.LlmResponse;
import dev.apidocs.core.ai.LlmStopReason;
import dev.apidocs.core.ai.TokenUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Scripted LLM for tests. Never touches the network. */
public final class FakeLlmClient implements LlmClient {

    private final Function<LlmRequest, LlmResponse> responder;
    private final List<LlmRequest> requests = new ArrayList<>();

    public FakeLlmClient(Function<LlmRequest, LlmResponse> responder) {
        this.responder = responder;
    }

    /** Answers with the given texts in order, then with empty text. */
    public static FakeLlmClient replying(String... texts) {
        Deque<String> queue = new ArrayDeque<>(List.of(texts));
        return new FakeLlmClient(request -> ok(queue.isEmpty() ? "" : queue.poll()));
    }

    /** Answers by request purpose; unknown purposes get empty text. */
    public static FakeLlmClient byPurpose(Map<String, String> textByPurpose) {
        return new FakeLlmClient(request -> ok(textByPurpose.getOrDefault(request.purpose(), "")));
    }

    public static LlmResponse ok(String text) {
        return new LlmResponse(text, LlmStopReason.COMPLETE, new TokenUsage(100, 50, 0), "fake-model");
    }

    @Override
    public String id() {
        return "fake:model";
    }

    @Override
    public synchronized LlmResponse complete(LlmRequest request) {
        requests.add(request);
        return responder.apply(request);
    }

    public synchronized List<LlmRequest> requests() {
        return List.copyOf(requests);
    }
}
