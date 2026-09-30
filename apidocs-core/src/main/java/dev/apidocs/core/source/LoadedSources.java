package dev.apidocs.core.source;

import dev.apidocs.core.model.Warning;
import java.nio.file.Path;
import java.util.List;

public record LoadedSources(Path root, List<SourceFile> javaFiles, List<Warning> warnings) {

    public LoadedSources {
        javaFiles = List.copyOf(javaFiles);
        warnings = List.copyOf(warnings);
    }
}
