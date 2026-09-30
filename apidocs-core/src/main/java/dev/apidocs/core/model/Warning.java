package dev.apidocs.core.model;

import java.util.Comparator;

/** Something the tool could not do perfectly; reported to the user, never fatal. */
public record Warning(String code, String message, String location) implements Comparable<Warning> {

    private static final Comparator<Warning> ORDER = Comparator.comparing(Warning::code)
            .thenComparing(Warning::location)
            .thenComparing(Warning::message);

    public Warning {
        location = location == null ? "" : location;
    }

    public static Warning of(String code, String message) {
        return new Warning(code, message, "");
    }

    @Override
    public int compareTo(Warning other) {
        return ORDER.compare(this, other);
    }
}
