package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.source.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaSourceParserTest {

    private static SourceFile file(String name, String content) {
        return new SourceFile(Path.of(name), "src/main/java/" + name, content);
    }

    @Test
    void parsesValidFilesAndReportsBrokenOnesAsWarnings() {
        SourceFile ok = file("A.java", "package x; public record A(String name) {}");
        SourceFile broken = file("B.java", "package x;\npublic class B {\n  void m( {\n}");

        ParseOutcome outcome = new JavaSourceParser().parse(List.of(ok, broken));

        assertThat(outcome.units()).hasSize(1);
        assertThat(outcome.units().get(0).packageName()).isEqualTo("x");
        assertThat(outcome.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo("PARSE_ERROR");
            assertThat(w.location()).startsWith("src/main/java/B.java:");
        });
    }

    @Test
    void supportsJava21Syntax() {
        SourceFile modern = file("Shape.java", """
                package x;
                public sealed interface Shape permits Circle, Square {
                    static String describe(Shape shape) {
                        return switch (shape) {
                            case Circle c -> "circle " + c.radius();
                            case Square s -> "square " + s.side();
                        };
                    }
                }
                record Circle(double radius) implements Shape {}
                record Square(double side) implements Shape {}
                """);

        ParseOutcome outcome = new JavaSourceParser().parse(List.of(modern));

        assertThat(outcome.warnings()).isEmpty();
        assertThat(outcome.units()).hasSize(1);
    }
}
