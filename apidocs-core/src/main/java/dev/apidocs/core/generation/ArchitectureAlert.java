package dev.apidocs.core.generation;

import java.util.List;

/** A problem found by a static rule; {@code arguments} fill the localized {@code alert.<code>.detail} message. */
public record ArchitectureAlert(String code, String subject, List<String> arguments) {

    public ArchitectureAlert {
        arguments = List.copyOf(arguments);
    }
}
