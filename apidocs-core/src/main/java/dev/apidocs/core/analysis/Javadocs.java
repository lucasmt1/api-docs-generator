package dev.apidocs.core.analysis;

import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;

public final class Javadocs {

    private Javadocs() {
    }

    /** Javadoc description (block tags such as {@code @param} excluded), whitespace collapsed; "" when absent. */
    public static String of(NodeWithJavadoc<?> node) {
        try {
            return node.getJavadoc()
                    .map(javadoc -> javadoc.getDescription().toText())
                    .map(text -> text.replaceAll("\\s+", " ").strip())
                    .orElse("");
        } catch (RuntimeException malformedJavadoc) {
            return "";
        }
    }
}
