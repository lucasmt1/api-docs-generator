package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.testsupport.FakeLlmClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachingLlmClientTest {

    @TempDir
    Path cache;

    private static LlmRequest request(String user) {
        return new LlmRequest("p", "system", user, null, 100);
    }

    @Test
    void servesRepeatedRequestsFromDiskWithZeroUsage() {
        FakeLlmClient fake = FakeLlmClient.replying("first", "second");
        CachingLlmClient client = new CachingLlmClient(fake, cache);

        LlmResponse original = client.complete(request("a"));
        LlmResponse cached = client.complete(request("a"));
        LlmResponse other = client.complete(request("b"));

        assertThat(original.text()).isEqualTo("first");
        assertThat(cached.text()).isEqualTo("first");
        assertThat(cached.usage()).isEqualTo(TokenUsage.ZERO);
        assertThat(other.text()).isEqualTo("second");
        assertThat(fake.requests()).hasSize(2);
        assertThat(client.id()).isEqualTo("fake:model");
    }

    @Test
    void doesNotCacheIncompleteAnswers() {
        FakeLlmClient refusing = new FakeLlmClient(r -> new LlmResponse("", LlmStopReason.REFUSAL, TokenUsage.ZERO, "m"));
        CachingLlmClient client = new CachingLlmClient(refusing, cache);

        client.complete(request("a"));
        client.complete(request("a"));

        assertThat(refusing.requests()).hasSize(2);
    }

    @Test
    void ignoresCorruptEntries() throws IOException {
        FakeLlmClient fake = FakeLlmClient.replying("first", "again");
        CachingLlmClient client = new CachingLlmClient(fake, cache);
        client.complete(request("a"));
        try (Stream<Path> files = Files.walk(cache)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.writeString(file, "{broken");
            }
        }

        assertThat(client.complete(request("a")).text()).isEqualTo("again");
    }

    @Test
    void ignoresEntriesThatAreJsonNull() throws IOException {
        FakeLlmClient fake = FakeLlmClient.replying("first", "again");
        CachingLlmClient client = new CachingLlmClient(fake, cache);
        client.complete(request("a"));
        try (Stream<Path> files = Files.walk(cache)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.writeString(file, "null");
            }
        }

        assertThat(client.complete(request("a")).text()).isEqualTo("again");
    }
}
