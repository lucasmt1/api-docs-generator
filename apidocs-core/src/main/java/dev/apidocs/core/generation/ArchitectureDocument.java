package dev.apidocs.core.generation;

import dev.apidocs.core.ai.narrative.AlertComment;
import dev.apidocs.core.ai.narrative.ArchitectureNarrative;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.render.MarkdownWriter;
import dev.apidocs.core.render.MermaidRenderer;
import java.util.List;
import java.util.Optional;

/** {@code architecture-overview.md}: diagrams, metrics and alerts from rules; commentary from the LLM. */
public final class ArchitectureDocument {

    private final MermaidRenderer mermaid = new MermaidRenderer();

    public String render(DocumentContext context) {
        Messages m = context.messages();
        ApiModel model = context.model();
        Optional<ArchitectureNarrative> narrative = context.narratives().architectureDoc();
        MarkdownWriter md = new MarkdownWriter().heading(1, m.get("doc.architecture.title") + " — " + model.project().name());

        md.heading(2, m.get("arch.summary"));
        md.paragraph(Texts.orMissing(narrative.map(ArchitectureNarrative::summary).orElse(""), m));

        md.heading(2, m.get("arch.layers"));
        if (narrative.isEmpty()) {
            md.paragraph(Texts.missing(m));
        } else {
            md.table(List.of(m.get("arch.col.layer"), m.get("arch.col.responsibility")),
                    narrative.get().layers().stream().map(layer -> List.of(layer.layer(), layer.description())).toList());
        }
        md.code("mermaid", mermaid.layers(model));

        if (!model.entities().isEmpty()) {
            md.heading(2, m.get("arch.dataModel"));
            md.code("mermaid", mermaid.entities(model.entities()));
        }

        md.heading(2, m.get("arch.metrics"));
        md.table(List.of(m.get("index.col.metric"), m.get("index.col.value")), Metrics.architecture(model, m));

        md.heading(2, m.get("arch.patterns"));
        Texts.section(md, m, narrative, ArchitectureNarrative::patterns, md::bullets);

        md.heading(2, m.get("arch.alerts"));
        if (context.alerts().isEmpty()) {
            md.paragraph(m.get("arch.noAlerts"));
        } else {
            md.table(List.of(m.get("arch.col.code"), m.get("arch.col.subject"), m.get("arch.col.detail")),
                    context.alerts().stream()
                            .map(alert -> List.of(MarkdownWriter.inlineCode(alert.code()), alert.subject(),
                                    m.get("alert." + alert.code() + ".detail", alert.arguments().toArray())))
                            .toList());
            for (ArchitectureAlert alert : context.alerts()) {
                narrative.flatMap(n -> n.alertComments().stream()
                                .filter(c -> c.code().equals(alert.code()) && c.subject().equals(alert.subject()))
                                .findFirst())
                        .ifPresent(comment -> alertComment(md, alert, comment, m));
            }
        }

        md.heading(2, m.get("arch.recommendations"));
        Texts.section(md, m, narrative, ArchitectureNarrative::recommendations, md::bullets);
        return md.build();
    }

    private static void alertComment(MarkdownWriter md, ArchitectureAlert alert, AlertComment comment, Messages m) {
        md.heading(3, m.get("alert." + alert.code() + ".title") + " — " + alert.subject());
        md.paragraph(comment.comment());
        if (Texts.present(comment.recommendation())) {
            md.paragraph("**" + m.get("arch.recommendation") + ":** " + comment.recommendation());
        }
    }
}
