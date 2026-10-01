package dev.apidocs.core.analysis;

import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.javadoc.description.JavadocDescriptionElement;
import com.github.javaparser.javadoc.description.JavadocInlineTag;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Javadocs {

    private static final Set<JavadocInlineTag.Type> CODE_TAGS = Set.of(JavadocInlineTag.Type.CODE,
            JavadocInlineTag.Type.LITERAL, JavadocInlineTag.Type.LINK, JavadocInlineTag.Type.LINKPLAIN,
            JavadocInlineTag.Type.VALUE);
    private static final Pattern BACKTICK_RUN = Pattern.compile("`+");

    private Javadocs() {
    }

    /**
     * Javadoc description (block tags such as {@code @param} excluded), whitespace collapsed; "" when absent.
     * Inline code, literal, link, linkplain and value tags become Markdown code spans, so generic types such as
     * {@code List<Item>} are not read as HTML tags.
     */
    public static String of(NodeWithJavadoc<?> node) {
        try {
            return node.getJavadoc()
                    .map(javadoc -> text(javadoc.getDescription()))
                    .map(text -> text.replaceAll("\\s+", " ").strip())
                    .orElse("");
        } catch (RuntimeException malformedJavadoc) {
            return "";
        }
    }

    private static String text(JavadocDescription description) {
        StringBuilder text = new StringBuilder();
        for (JavadocDescriptionElement element : description.getElements()) {
            if (element instanceof JavadocInlineTag tag && CODE_TAGS.contains(tag.getType())
                    && !tag.getContent().isBlank()) {
                text.append(codeSpan(tag.getContent().strip()));
            } else {
                text.append(element.toText());
            }
        }
        return text.toString();
    }

    /** CommonMark code span whose fence is longer than any backtick run inside the content. */
    private static String codeSpan(String content) {
        int longestRun = 0;
        Matcher run = BACKTICK_RUN.matcher(content);
        while (run.find()) {
            longestRun = Math.max(longestRun, run.group().length());
        }
        String fence = "`".repeat(longestRun + 1);
        String padding = content.startsWith("`") || content.endsWith("`") ? " " : "";
        return fence + padding + content + padding + fence;
    }
}
