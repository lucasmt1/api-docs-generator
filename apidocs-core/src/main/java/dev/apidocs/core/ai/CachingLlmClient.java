package dev.apidocs.core.ai;

import dev.apidocs.core.support.Hashing;
import dev.apidocs.core.support.JsonSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Disk cache: same prompt and model give the same answer at zero cost (stable, repeatable output). */
public final class CachingLlmClient implements LlmClient {

    record CacheEntry(String model, String text) {
    }

    private static final System.Logger LOG = System.getLogger(CachingLlmClient.class.getName());

    private final LlmClient delegate;
    private final Path directory;

    public CachingLlmClient(LlmClient delegate, Path directory) {
        this.delegate = delegate;
        this.directory = directory;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String key = Hashing.sha256(String.join("\u0000", delegate.id(), request.systemPrompt(), request.userPrompt(),
                request.outputSchema() == null ? "" : request.outputSchema().toString(),
                String.valueOf(request.maxOutputTokens())));
        Path file = directory.resolve(key.substring(0, 2)).resolve(key + ".json");
        Optional<LlmResponse> cached = read(file);
        if (cached.isPresent()) {
            return cached.get();
        }
        LlmResponse response = delegate.complete(request);
        if (response.stopReason() == LlmStopReason.COMPLETE) {
            write(file, new CacheEntry(response.model(), response.text()));
        }
        return response;
    }

    private static Optional<LlmResponse> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CacheEntry entry = JsonSupport.mapper().readValue(file.toFile(), CacheEntry.class);
            if (entry == null || entry.text() == null) {
                return Optional.empty();
            }
            return Optional.of(new LlmResponse(entry.text(), LlmStopReason.COMPLETE, TokenUsage.ZERO, entry.model()));
        } catch (IOException corrupt) {
            return Optional.empty();
        }
    }

    private static void write(Path file, CacheEntry entry) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "entry", ".tmp");
            Files.writeString(temp, JsonSupport.toPrettyJson(entry));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Could not write cache entry {0}: {1}", file, e.getMessage());
        }
    }
}
