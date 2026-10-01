package dev.apidocs.core.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code step}/{@code totalSteps} are 0 when the stage has no sub-steps. */
public record ProgressEvent(Stage stage, String message, Map<String, Integer> counters, int step, int totalSteps) {

    public ProgressEvent {
        counters = Collections.unmodifiableMap(new LinkedHashMap<>(counters));
    }

    public static ProgressEvent of(Stage stage, String message) {
        return new ProgressEvent(stage, message, Map.of(), 0, 0);
    }
}
