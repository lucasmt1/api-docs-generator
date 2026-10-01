package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

class JavadocsTest {

    private static String javadocOf(String comment) {
        MethodDeclaration method = StaticJavaParser.parse("class C {\n" + comment + "\nvoid m() {}\n}")
                .findFirst(MethodDeclaration.class).orElseThrow();
        return Javadocs.of(method);
    }

    @Test
    void rendersCodeLikeInlineTagsAsCodeSpans() {
        assertThat(javadocOf("/** Returns a {@code List<Item>} of all items. */"))
                .isEqualTo("Returns a `List<Item>` of all items.");
        assertThat(javadocOf("/** See {@link Orders#find(Long) find} and {@linkplain Item}, {@literal a<b},"
                + " {@value #MAX}. */"))
                .isEqualTo("See `Orders#find(Long) find` and `Item`, `a<b`, `#MAX`.");
    }

    @Test
    void keepsCodeSpansIntactWhenTheContentHasBackticks() {
        assertThat(javadocOf("/** Quotes {@code a`b} and {@code `x}. */")).isEqualTo("Quotes ``a`b`` and `` `x ``.");
    }

    @Test
    void keepsOtherTagsAndCollapsesWhitespace() {
        assertThat(javadocOf("""
                /**
                 * First line
                 *   {@inheritDoc} and {@code
                 *   Map<String, Long>}.
                 *
                 * @param x ignored
                 */""")).isEqualTo("First line {@inheritDoc} and `Map<String, Long>`.");
        assertThat(javadocOf("/** No tags here. */")).isEqualTo("No tags here.");
    }
}
