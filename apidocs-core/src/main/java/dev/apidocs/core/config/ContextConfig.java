package dev.apidocs.core.config;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Project context written by the maintainers ({@code context} block of {@code .apidocs.yml}). */
public record ContextConfig(String description, String audience, Map<String, String> glossary, String instructions) {

    public static final ContextConfig EMPTY = new ContextConfig("", "", Map.of(), "");

    public ContextConfig {
        description = description == null ? "" : description.strip();
        audience = audience == null ? "" : audience.strip();
        glossary = glossary == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(glossary));
        instructions = instructions == null ? "" : instructions.strip();
    }

    /** True when there is nothing to add to the system prompt besides the audience. */
    public boolean isEmpty() {
        return description.isEmpty() && glossary.isEmpty() && instructions.isEmpty();
    }
}
