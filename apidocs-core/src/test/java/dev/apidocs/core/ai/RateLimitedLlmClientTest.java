package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.testsupport.FakeLlmClient;
import dev.apidocs.core.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitedLlmClientTest {

    @Test
    void waitsWhenTheWindowIsFull() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        List<Duration> sleeps = new ArrayList<>();
        Sleeper sleeper = duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        };
        FakeLlmClient fake = FakeLlmClient.replying("a", "b", "c");
        RateLimitedLlmClient client = new RateLimitedLlmClient(fake, 2, clock, sleeper);
        LlmRequest request = new LlmRequest("p", "s", "u", null, 10);

        client.complete(request);
        clock.advance(Duration.ofSeconds(10));
        client.complete(request);
        client.complete(request);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(50));
        assertThat(fake.requests()).hasSize(3);
    }

    @Test
    void zeroDisablesTheLimit() {
        List<Duration> sleeps = new ArrayList<>();
        RateLimitedLlmClient client = new RateLimitedLlmClient(FakeLlmClient.replying("a", "b"), 0,
                new MutableClock(Instant.EPOCH), sleeps::add);

        client.complete(new LlmRequest("p", "s", "u", null, 10));
        client.complete(new LlmRequest("p", "s", "u", null, 10));

        assertThat(sleeps).isEmpty();
    }
}
