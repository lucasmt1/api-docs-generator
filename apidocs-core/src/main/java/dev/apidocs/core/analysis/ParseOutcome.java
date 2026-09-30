package dev.apidocs.core.analysis;

import dev.apidocs.core.model.Warning;
import java.util.List;

public record ParseOutcome(List<ParsedUnit> units, List<Warning> warnings) {

    public ParseOutcome {
        units = List.copyOf(units);
        warnings = List.copyOf(warnings);
    }
}
