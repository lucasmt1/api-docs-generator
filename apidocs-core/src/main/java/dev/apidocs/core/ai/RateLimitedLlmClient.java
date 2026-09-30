package dev.apidocs.core.ai;

import dev.apidocs.core.CancelledException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps calls under N per rolling minute (free tiers such as Gemini's). */
public final class RateLimitedLlmClient implements LlmClient {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final LlmClient delegate;
    private final int requestsPerMinute;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Deque<Instant> recent = new ArrayDeque<>();

    public RateLimitedLlmClient(LlmClient delegate, int requestsPerMinute, Clock clock, Sleeper sleeper) {
        this.delegate = delegate;
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public synchronized LlmResponse complete(LlmRequest request) {
        if (requestsPerMinute > 0) {
            acquire();
        }
        return delegate.complete(request);
    }

    private void acquire() {
        prune(clock.instant());
        if (recent.size() >= requestsPerMinute) {
            Duration wait = Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW));
            if (!wait.isNegative() && !wait.isZero()) {
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancelledException("Interrupted while waiting for the rate limit");
                }
            }
            prune(clock.instant());
        }
        recent.addLast(clock.instant());
    }

    private void prune(Instant now) {
        while (!recent.isEmpty() && !recent.peekFirst().plus(WINDOW).isAfter(now)) {
            recent.removeFirst();
        }
    }
}
