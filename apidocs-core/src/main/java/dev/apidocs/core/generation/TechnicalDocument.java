package dev.apidocs.core.generation;

import dev.apidocs.core.ai.narrative.RuleGroup;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ProjectInfo;
import dev.apidocs.core.render.MarkdownWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@code technical-documentation.md}. */
public final class TechnicalDocument {

    public String render(DocumentContext context) {
        Messages m = context.messages();
        ApiModel model = context.model();
        ProjectInfo project = model.project();
        Optional<TechnicalDocNarrative> narrative = context.narratives().technicalDoc();
        MarkdownWriter md = new MarkdownWriter().heading(1, m.get("doc.technical.title") + " — " + project.name());

        md.heading(2, m.get("tech.overview"));
        md.paragraph(Texts.orMissing(narrative.map(TechnicalDocNarrative::overview).filter(Texts::present)
                .orElse(project.description()), m));

        md.heading(2, m.get("tech.stack"));
        md.table(List.of(m.get("tech.col.item"), m.get("tech.col.value")), List.of(
                List.of("Java", orEmpty(project.javaVersion())),
                List.of("Spring Boot", orEmpty(project.springBootVersion())),
                List.of(m.get("tech.buildTool"), project.buildTool()),
                List.of(m.get("tech.dependencies"), Texts.codeList(project.dependencies()))));

        md.heading(2, m.get("tech.numbers"));
        md.table(List.of(m.get("index.col.metric"), m.get("index.col.value")), Metrics.summary(model, m));

        List<List<String>> configuration = new ArrayList<>();
        if (!project.applicationName().isBlank()) {
            configuration.add(List.of(m.get("tech.applicationName"), MarkdownWriter.inlineCode(project.applicationName())));
        }
        if (!project.contextPath().isBlank()) {
            configuration.add(List.of(m.get("tech.contextPath"), MarkdownWriter.inlineCode(project.contextPath())));
        }
        if (!configuration.isEmpty()) {
            md.heading(2, m.get("tech.configuration"));
            md.table(List.of(m.get("tech.col.item"), m.get("tech.col.value")), configuration);
        }

        String runCommand = switch (project.buildTool()) {
            case "maven" -> "mvn spring-boot:run";
            case "gradle" -> "./gradlew bootRun";
            default -> "";
        };
        if (!runCommand.isEmpty()) {
            md.heading(2, m.get("tech.howToRun"));
            md.code("bash", runCommand);
        }

        md.heading(2, m.get("tech.domainConcepts"));
        Texts.section(md, m, narrative, TechnicalDocNarrative::domainConcepts,
                concepts -> md.table(List.of(m.get("tech.col.concept"), m.get("tech.col.description")),
                        concepts.stream().map(c -> List.of(c.name(), c.description())).toList()));

        md.heading(2, m.get("tech.businessRules"));
        Texts.section(md, m, narrative, TechnicalDocNarrative::businessRules, groups -> {
            for (RuleGroup group : groups) {
                md.heading(3, group.domain());
                md.bullets(group.rules());
            }
        });

        md.heading(2, m.get("tech.errorHandling"));
        md.paragraph(Texts.orMissing(narrative.map(TechnicalDocNarrative::errorHandling).orElse(""), m));
        md.table(List.of(m.get("tech.col.exception"), m.get("tech.col.status")),
                model.exceptionMappings().stream()
                        .map(mapping -> List.of(MarkdownWriter.inlineCode(mapping.exception()), Texts.status(mapping.status())))
                        .toList());

        md.heading(2, m.get("tech.glossary"));
        Texts.section(md, m, narrative, TechnicalDocNarrative::glossary,
                entries -> md.table(List.of(m.get("tech.col.term"), m.get("tech.col.definition")),
                        entries.stream().map(g -> List.of(g.term(), g.definition())).toList()));
        return md.build();
    }

    private static String orEmpty(String value) {
        return value.isBlank() ? Texts.EMPTY : value;
    }
}
