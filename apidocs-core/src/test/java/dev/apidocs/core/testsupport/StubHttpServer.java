package dev.apidocs.core.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local HTTP server that records requests and replays queued replies. */
public final class StubHttpServer implements AutoCloseable {

    public record Recorded(String method, String path, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    public record Reply(int status, Map<String, String> headers, String body) {

        public static Reply json(int status, String body) {
            return new Reply(status, Map.of("Content-Type", "application/json"), body);
        }

        public static Reply sse(String body) {
            return new Reply(200, Map.of("Content-Type", "text/event-stream"), body);
        }

        public Reply withHeader(String name, String value) {
            Map<String, String> copy = new LinkedHashMap<>(headers);
            copy.put(name, value);
            return new Reply(status, copy, body);
        }
    }

    private final HttpServer server;
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    public StubHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public StubHttpServer enqueue(Reply reply) {
        replies.add(reply);
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(exchange.getRequestHeaders());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers, body));
        Reply reply = replies.poll();
        if (reply == null) {
            reply = Reply.json(500, "{\"error\":\"no reply queued\"}");
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
