package dev.apidocs.core.generation;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.render.MarkdownWriter;
import java.util.List;

/** {@code README.md} of the output folder. */
public final class IndexDocument {

    public String render(DocumentContext context, List<Warning> warnings) {
        Messages m = context.messages();
        ApiModel model = context.model();
        MarkdownWriter md = new MarkdownWriter()
                .heading(1, model.project().name() + " — " + m.get("index.title"))
                .paragraph(m.get("index.intro", context.narrativeSource()))
                .heading(2, m.get("index.documents"))
                .table(List.of(m.get("index.col.document"), m.get("index.col.contents")), List.of(
                        List.of(link(m.get("doc.technical.title"), "technical-documentation.md"), m.get("index.doc.technical")),
                        List.of(link(m.get("doc.api.title"), "api-reference.md"), m.get("index.doc.api")),
                        List.of(link(m.get("doc.architecture.title"), "architecture-overview.md"), m.get("index.doc.architecture")),
                        List.of(link("openapi.yaml", "openapi.yaml"), m.get("index.doc.openapi")),
                        List.of(link("model.json", "model.json"), m.get("index.doc.model"))))
                .heading(2, m.get("index.summary"))
                .table(List.of(m.get("index.col.metric"), m.get("index.col.value")), Metrics.summary(model, m))
                .heading(2, m.get("index.warnings") + " (" + warnings.size() + ")");
        if (warnings.isEmpty()) {
            md.paragraph(m.get("index.noWarnings"));
        } else {
            md.table(List.of(m.get("index.col.code"), m.get("index.col.location"), m.get("index.col.message")),
                    warnings.stream()
                            .map(w -> List.of(MarkdownWriter.inlineCode(w.code()), w.location(), w.message()))
                            .toList());
        }
        return md.build();
    }

    private static String link(String text, String target) {
        return "[" + text + "](" + target + ")";
    }
}
