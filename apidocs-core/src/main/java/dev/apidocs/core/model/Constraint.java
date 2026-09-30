package dev.apidocs.core.model;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** A Bean Validation constraint, e.g. {@code @Size(min=1, max=5)}. Attributes are kept sorted by name. */
public record Constraint(String name, Map<String, String> attributes) {

    public Constraint {
        attributes = Collections.unmodifiableMap(new TreeMap<>(attributes));
    }

    public static Constraint of(String name) {
        return new Constraint(name, Map.of());
    }

    public static Constraint of(String name, String key, String value) {
        return new Constraint(name, Map.of(key, value));
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    public String describe() {
        if (attributes.isEmpty()) {
            return "@" + name;
        }
        return "@" + name + attributes.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
