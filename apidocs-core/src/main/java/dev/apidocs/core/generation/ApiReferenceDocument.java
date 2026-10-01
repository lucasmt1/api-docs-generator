package dev.apidocs.core.generation;

import dev.apidocs.core.ai.narrative.ControllerNarrative;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.render.MarkdownWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** {@code api-reference.md}: facts from the code, texts from the LLM. */
public final class ApiReferenceDocument {

    public String render(DocumentContext context) {
        Messages m = context.messages();
        ApiModel model = context.model();
        MarkdownWriter md = new MarkdownWriter()
                .heading(1, m.get("doc.api.title"))
                .paragraph(m.get("api.intro", String.valueOf(model.endpointCount()), String.valueOf(model.controllers().size())))
                .heading(2, m.get("api.contents"))
                .bullets(model.controllers().stream()
                        .map(c -> "[" + c.name() + "](" + MarkdownWriter.anchor(c.name()) + ") — "
                                + m.get("api.endpointCount", String.valueOf(c.endpoints().size())))
                        .toList());
        Map<String, SchemaInfo> schemas = model.schemasByName();
        for (ControllerInfo controller : model.controllers()) {
            md.heading(2, controller.name());
            String summary = context.narratives().controller(controller.name())
                    .map(ControllerNarrative::controllerSummary).filter(Texts::present)
                    .orElse(controller.description());
            md.paragraph(Texts.orMissing(summary, m));
            for (EndpointInfo endpoint : controller.endpoints()) {
                endpoint(md, endpoint, context.narratives().endpoint(controller.name(), endpoint.id()), schemas, m);
            }
        }
        md.heading(2, m.get("api.schemas"));
        model.schemas().forEach(schema -> schema(md, schema, m));
        return md.build();
    }

    private void endpoint(MarkdownWriter md, EndpointInfo endpoint, Optional<EndpointNarrative> narrative,
            Map<String, SchemaInfo> schemas, Messages m) {
        String title = narrative.map(EndpointNarrative::title).filter(Texts::present)
                .or(() -> Optional.of(endpoint.summary()).filter(Texts::present))
                .map(text -> " — " + text).orElse("");
        md.heading(3, MarkdownWriter.inlineCode(endpoint.method() + " " + endpoint.path()) + title);
        if (!endpoint.security().isBlank()) {
            md.quote("🔒 " + m.get("api.security") + ": " + MarkdownWriter.inlineCode(endpoint.security()));
        }
        if (endpoint.deprecated()) {
            md.quote("⚠️ " + m.get("api.deprecated"));
        }
        if (narrative.isPresent()) {
            md.paragraph(narrative.get().summary());
            md.paragraph(narrative.get().description());
        } else {
            md.paragraph(Texts.orMissing(endpoint.description(), m));
        }
        narrative.map(EndpointNarrative::businessRules).filter(rules -> !rules.isEmpty()).ifPresent(rules -> {
            md.paragraph("**" + m.get("api.businessRules") + "**");
            md.bullets(rules);
        });
        if (!endpoint.parameters().isEmpty()) {
            md.paragraph("**" + m.get("api.parameters") + "**");
            md.table(List.of(m.get("api.col.name"), m.get("api.col.in"), m.get("api.col.type"), m.get("api.col.required"),
                            m.get("api.col.default"), m.get("api.col.constraints")),
                    endpoint.parameters().stream().map(p -> parameterRow(p, m)).toList());
        }
        if (endpoint.requestBody() != null) {
            TypeRef type = endpoint.requestBody().type();
            md.paragraph("**" + m.get("api.requestBody") + "** — " + MarkdownWriter.inlineCode(type.display())
                    + (endpoint.requestBody().validated() ? " (" + m.get("api.validated") + ")" : ""));
            objectSchema(type, schemas).ifPresent(schema -> fieldTable(md, schema, m));
        }
        md.paragraph("**" + m.get("api.responses") + "**");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(Texts.status(endpoint.response().status()),
                endpoint.response().hasBody() ? MarkdownWriter.inlineCode(endpoint.response().body().display()) : Texts.EMPTY,
                m.get("api.response.success")));
        for (ErrorResponse error : endpoint.errors()) {
            String when = narrative.flatMap(n -> n.whenFor(error.status())).orElse("");
            rows.add(List.of(Texts.status(error.status()), MarkdownWriter.inlineCode(error.exception()), when));
        }
        md.table(List.of(m.get("api.col.status"), m.get("api.col.response"), m.get("api.col.when")), rows);

        String request = narrative.map(EndpointNarrative::requestExample).orElse("");
        String response = narrative.map(EndpointNarrative::responseExample).orElse("");
        if (Texts.present(request) || Texts.present(response)) {
            md.paragraph("**" + m.get("api.examples") + "**");
            if (Texts.present(request)) {
                md.paragraph(m.get("api.example.request") + ":");
                md.code("json", request);
            }
            if (Texts.present(response)) {
                md.paragraph(m.get("api.example.response") + ":");
                md.code("json", response);
            }
        }
    }

    private static List<String> parameterRow(ParameterInfo parameter, Messages m) {
        return List.of(MarkdownWriter.inlineCode(parameter.name()),
                m.get("in." + parameter.in().name().toLowerCase(Locale.ROOT)),
                MarkdownWriter.inlineCode(parameter.type().display()),
                Texts.yesNo(parameter.required(), m),
                parameter.defaultValue(),
                Texts.constraints(parameter.constraints()));
    }

    private void schema(MarkdownWriter md, SchemaInfo schema, Messages m) {
        md.heading(3, schema.name());
        md.paragraph(schema.description());
        if (schema.kind() == SchemaKind.ENUM) {
            md.paragraph("**" + m.get("api.enumValues") + "**: " + schema.enumValues().stream()
                    .map(MarkdownWriter::inlineCode).collect(Collectors.joining(", ")));
        } else {
            fieldTable(md, schema, m);
        }
    }

    private static void fieldTable(MarkdownWriter md, SchemaInfo schema, Messages m) {
        md.table(List.of(m.get("api.col.field"), m.get("api.col.type"), m.get("api.col.required"),
                        m.get("api.col.constraints"), m.get("api.col.description")),
                schema.fields().stream().map(field -> fieldRow(field, m)).toList());
    }

    private static List<String> fieldRow(FieldInfo field, Messages m) {
        return List.of(MarkdownWriter.inlineCode(field.name()), MarkdownWriter.inlineCode(field.type().display()),
                Texts.yesNo(field.required(), m), Texts.constraints(field.constraints()), field.description());
    }

    private static Optional<SchemaInfo> objectSchema(TypeRef type, Map<String, SchemaInfo> schemas) {
        TypeRef target = type instanceof ArrayOf array ? array.items() : type;
        if (target instanceof ObjectRef ref) {
            return Optional.ofNullable(schemas.get(ref.schemaName())).filter(s -> s.kind() == SchemaKind.OBJECT);
        }
        return Optional.empty();
    }
}
