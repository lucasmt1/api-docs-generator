package dev.apidocs.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownWriterTest {

    @Test
    void buildsBlocksSeparatedByBlankLines() {
        String markdown = new MarkdownWriter()
                .heading(1, "Title")
                .paragraph("  Some text.  ")
                .paragraph("   ")
                .bullets(List.of("one", "two\nlines"))
                .table(List.of("A", "B"), List.of(List.of("x|y", ""), List.of("1", "2")))
                .table(List.of("Empty"), List.of())
                .code("json", "{\"a\": 1}\n")
                .quote("Note")
                .build();

        assertThat(markdown).isEqualTo("""
                # Title

                Some text.

                - one
                - two lines

                | A | B |
                | --- | --- |
                | x\\|y |  |
                | 1 | 2 |

                ```json
                {"a": 1}
                ```

                > Note
                """);
    }

    @Test
    void fenceIsLongerThanAnyBacktickRunInsideTheCode() {
        String withLoneFence = new MarkdownWriter().code("java", "a();\n```\n<script>x</script>\nb();").build();
        String withLongerRun = new MarkdownWriter().code("java", "s = \"`````\";\n`````\nend();").build();

        assertThat(withLoneFence).isEqualTo("""
                ````java
                a();
                ```
                <script>x</script>
                b();
                ````
                """);
        assertThat(withLongerRun).isEqualTo("""
                ``````java
                s = "`````";
                `````
                end();
                ``````
                """);
    }

    @Test
    void sanitizesTheFenceLanguage() {
        assertThat(new MarkdownWriter().code(null, "x").build()).isEqualTo("```\nx\n```\n");
        assertThat(new MarkdownWriter().code(" ja`va\n", "x").build()).isEqualTo("```java\nx\n```\n");
    }

    @Test
    void outputUsesOnlyLineFeeds() {
        String markdown = new MarkdownWriter()
                .heading(2, "A\r\nB\rC")
                .paragraph("one\r\ntwo\rthree")
                .code("java", "x();\r\ny();\rz();")
                .table(List.of("H"), List.of(List.of("p\rq")))
                .build();

        assertThat(markdown).doesNotContain("\r");
        assertThat(markdown).isEqualTo("""
                ## A B C

                one
                two
                three

                ```java
                x();
                y();
                z();
                ```

                | H |
                | --- |
                | p q |
                """);
    }

    @Test
    void escapesInlineCodeAndBuildsAnchors() {
        assertThat(MarkdownWriter.inlineCode("a")).isEqualTo("`a`");
        assertThat(MarkdownWriter.inlineCode("a`b")).isEqualTo("`` a`b ``");
        assertThat(MarkdownWriter.anchor("OrderController")).isEqualTo("#ordercontroller");
        assertThat(MarkdownWriter.anchor("Regras de negócio")).isEqualTo("#regras-de-negócio");
        assertThat(MarkdownWriter.cell("a\nb")).isEqualTo("a b");
    }
}
