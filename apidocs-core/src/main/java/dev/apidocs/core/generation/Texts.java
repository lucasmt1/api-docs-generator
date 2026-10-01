package dev.apidocs.core.generation;

import dev.apidocs.core.analysis.HttpStatuses;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.render.MarkdownWriter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Texts {

    static final String EMPTY = "—";

    private Texts() {
    }

    static boolean present(String text) {
        return text != null && !text.isBlank();
    }

    static String missing(Messages messages) {
        return "_" + messages.get("narrative.missing") + "_";
    }

    static String orMissing(String text, Messages messages) {
        return present(text) ? text : missing(messages);
    }

    /**
     * A list section written by the LLM: the localized placeholder when there is no narrative, {@link #EMPTY} when
     * the narrative has nothing for this section, otherwise {@code render} writes the items.
     */
    static <N, T> void section(MarkdownWriter md, Messages messages, Optional<N> narrative,
            Function<N, List<T>> items, Consumer<List<T>> render) {
        if (narrative.isEmpty()) {
            md.paragraph(missing(messages));
            return;
        }
        List<T> list = items.apply(narrative.get());
        if (list.isEmpty()) {
            md.paragraph(EMPTY);
        } else {
            render.accept(list);
        }
    }

    static String status(int code) {
        return code + " " + HttpStatuses.reason(code);
    }

    static String yesNo(boolean value, Messages messages) {
        return messages.get(value ? "yes" : "no");
    }

    static String constraints(List<Constraint> constraints) {
        return constraints.stream().map(c -> MarkdownWriter.inlineCode(c.describe())).collect(Collectors.joining(" "));
    }

    static String codeList(List<String> values) {
        return values.isEmpty() ? EMPTY
                : values.stream().map(MarkdownWriter::inlineCode).collect(Collectors.joining(", "));
    }
}
