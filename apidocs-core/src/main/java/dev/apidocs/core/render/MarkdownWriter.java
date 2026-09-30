package dev.apidocs.core.render;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Small fluent builder producing GitHub-flavored Markdown with deterministic spacing. */
public final class MarkdownWriter {

    private final StringBuilder out = new StringBuilder();

    public MarkdownWriter heading(int level, String text) {
        out.append("#".repeat(level)).append(' ').append(inline(text)).append("\n\n");
        return this;
    }

    public MarkdownWriter paragraph(String text) {
        if (text != null && !text.isBlank()) {
            out.append(text.strip()).append("\n\n");
        }
        return this;
    }

    public MarkdownWriter bullets(List<String> items) {
        if (items.isEmpty()) {
            return this;
        }
        items.forEach(item -> out.append("- ").append(inline(item)).append('\n'));
        out.append('\n');
        return this;
    }

    public MarkdownWriter table(List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return this;
        }
        out.append(row(headers));
        out.append(headers.stream().map(header -> "---").collect(Collectors.joining(" | ", "| ", " |"))).append('\n');
        rows.forEach(r -> out.append(row(r)));
        out.append('\n');
        return this;
    }

    /**
     * Fenced code block. The code is untrusted source text, so the fence is always longer than any backtick run it
     * contains, and the info string can carry neither whitespace nor backticks.
     */
    public MarkdownWriter code(String language, String code) {
        String body = code.strip();
        String fence = "`".repeat(Math.max(3, longestBacktickRun(body) + 1));
        String info = language == null ? "" : language.replaceAll("[\\s`]", "");
        out.append(fence).append(info).append('\n').append(body).append('\n').append(fence).append("\n\n");
        return this;
    }

    public MarkdownWriter quote(String text) {
        out.append("> ").append(inline(text)).append("\n\n");
        return this;
    }

    public MarkdownWriter raw(String text) {
        out.append(text);
        return this;
    }

    /** The document, with {@code \n} as the only line ending. */
    public String build() {
        return out.toString().replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }

    /** Table cell: pipes escaped, line breaks flattened. */
    public static String cell(String text) {
        return text == null ? "" : inline(text).replace("|", "\\|");
    }

    public static String inlineCode(String text) {
        return text.contains("`") ? "`` " + text + " ``" : "`" + text + "`";
    }

    /** GitHub heading anchor. */
    public static String anchor(String heading) {
        String slug = heading.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} _-]", "")
                .replace(' ', '-');
        return "#" + slug;
    }

    private static String row(List<String> cells) {
        return cells.stream().map(MarkdownWriter::cell).collect(Collectors.joining(" | ", "| ", " |")) + "\n";
    }

    private static String inline(String text) {
        return text == null ? "" : text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').strip();
    }

    private static int longestBacktickRun(String text) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            run = text.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return longest;
    }
}
