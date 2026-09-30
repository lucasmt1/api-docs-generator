package dev.apidocs.core.testsupport;

import dev.apidocs.core.analysis.JavaSourceParser;
import dev.apidocs.core.analysis.ParseOutcome;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.source.SourceFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses inline Java sources for tests. */
public final class JavaSnippets {

    private JavaSnippets() {
    }

    public static List<ParsedUnit> parse(String... sources) {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < sources.length; i++) {
            String name = "src/main/java/Snippet" + i + ".java";
            files.add(new SourceFile(Path.of(name), name, sources[i]));
        }
        ParseOutcome outcome = new JavaSourceParser().parse(files);
        if (!outcome.warnings().isEmpty()) {
            throw new AssertionError("Snippet failed to parse: " + outcome.warnings());
        }
        return outcome.units();
    }

    public static TypeIndex index(String... sources) {
        return TypeIndex.build(parse(sources));
    }
}
