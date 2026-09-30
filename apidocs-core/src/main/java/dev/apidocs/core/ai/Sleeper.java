package dev.apidocs.core.ai;

import java.time.Duration;

/** Indirection over {@link Thread#sleep} so retry and rate-limit logic is testable without waiting. */
@FunctionalInterface
public interface Sleeper {

    Sleeper REAL = duration -> Thread.sleep(duration.toMillis());

    void sleep(Duration duration) throws InterruptedException;
}
