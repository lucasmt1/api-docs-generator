package dev.apidocs.core.ai.narrative;

import dev.apidocs.core.ai.Describe;

public record ErrorScenario(
        @Describe("HTTP status code.") int status,
        @Describe("When it happens, one sentence.") String when) {
}
