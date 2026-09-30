package dev.apidocs.core.model;

import java.util.Collection;
import java.util.List;

public final class Warnings {

    private Warnings() {
    }

    public static List<Warning> sortedDistinct(Collection<Warning> warnings) {
        return warnings.stream().distinct().sorted().toList();
    }
}
