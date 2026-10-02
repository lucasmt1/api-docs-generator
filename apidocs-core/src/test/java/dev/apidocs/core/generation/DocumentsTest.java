package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.ai.narrative.AlertComment;
import dev.apidocs.core.ai.narrative.ArchitectureNarrative;
import dev.apidocs.core.ai.narrative.ControllerNarrative;
import dev.apidocs.core.ai.narrative.DomainConcept;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.ai.narrative.ErrorScenario;
import dev.apidocs.core.ai.narrative.GlossaryEntry;
import dev.apidocs.core.ai.narrative.LayerDescription;
import dev.apidocs.core.ai.narrative.RuleGroup;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.ModelFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentsTest {

    private final ApiModel model = ModelFixtures.orderApi();
    private final List<ArchitectureAlert> alerts = new ArchitectureRules().evaluate(model);

    private static NarrativeSet narratives() {
        EndpointNarrative create = new EndpointNarrative("OrderController#create", "Criar pedido", "Cria um pedido.",
                "Reserva o estoque e grava o pedido.", List.of("Pelo menos um item"),
                List.of(new ErrorScenario(409, "Um produto está sem estoque")),
                "{\n  \"customerId\": 1\n}", "{\n  \"id\": 10\n}");
        TechnicalDocNarrative technical = new TechnicalDocNarrative("Orders system.",
                List.of(new DomainConcept("Order", "A purchase.")),
                List.of(new RuleGroup("Orders", List.of("Stock is reserved"))),
                "Errors return ApiError.", List.of(new GlossaryEntry("SKU", "Stock keeping unit")));
        ArchitectureNarrative architecture = new ArchitectureNarrative("Layered architecture.",
                List.of(new LayerDescription("Controllers", "HTTP adapters")), List.of("DTO mapping"),
                List.of(new AlertComment("ENTITY_EXPOSED", "Customer", "Leaks internals.", "Use a DTO.")),
                List.of("Add a CustomerResponse DTO"));
        return new NarrativeSet(Map.of("OrderController", new ControllerNarrative("Gerencia pedidos.", List.of(create))),
                technical, architecture);
    }

    private DocumentContext context(String language, NarrativeSet narratives) {
        return new DocumentContext(model, narratives, alerts, Messages.forLanguage(language), "fake:model");
    }

    @Test
    void rendersTheApiReferenceInPortuguese() {
        String doc = new ApiReferenceDocument().render(context("pt-BR", narratives()));

        assertThat(doc)
                .startsWith("# Referência da API\n\n3 endpoints em 1 controllers.\n")
                .contains("- [OrderController](#ordercontroller) — 3 endpoints")
                .contains("## OrderController\n\nGerencia pedidos.")
                .contains("### `POST /api/orders` — Criar pedido\n\n> 🔒 Requer: `hasRole('USER')`\n\nCria um pedido.\n\nReserva o estoque e grava o pedido.")
                .contains("**Regras de negócio**\n\n- Pelo menos um item")
                .contains("**Corpo da requisição** — `CreateOrderRequest` (validado com @Valid)")
                .contains("| `items` | `array<OrderItemRequest>` | sim | `@NotEmpty` `@Size(max=50)` | Items to buy. |")
                .contains("| 201 Created | `OrderResponse` | Sucesso |")
                .contains("| 409 Conflict | `InsufficientStockException` | Um produto está sem estoque |")
                .contains("| 404 Not Found | `NotFoundException` |  |")
                .contains("Requisição:\n\n```json\n{\n  \"customerId\": 1\n}\n```")
                .contains("| `id` | path | `integer (int64)` | sim |  |  |")
                .contains("### `GET /api/orders/{id}/customer`\n\n> ⚠️ Obsoleto\n\n_Texto não gerado")
                .contains("## Schemas\n\n### CreateOrderRequest\n\nData to place an order.")
                .contains("### OrderStatus\n\nLifecycle of an order.\n\n**Valores**: `CREATED`, `PAID`")
                .endsWith("\n")
                .doesNotContain("\n\n\n");
    }

    @Test
    void rendersTheTechnicalDocumentation() {
        String doc = new TechnicalDocument().render(context("en", narratives()));

        assertThat(doc)
                .startsWith("# Technical Documentation — Orders API\n\n## Overview\n\nOrders system.")
                .contains("| Java | 21 |")
                .contains("| Spring Boot | 4.1.1 |")
                .contains("| Main dependencies | `spring-boot-starter-webmvc` |")
                .contains("| Application name | `orders-api` |")
                .contains("## How to run\n\n```bash\nmvn spring-boot:run\n```")
                .contains("| Order | A purchase. |")
                .contains("### Orders\n\n- Stock is reserved")
                .contains("Errors return ApiError.")
                .contains("| `NotFoundException` | 404 Not Found |")
                .contains("| SKU | Stock keeping unit |")
                .doesNotContain("Context path");
    }

    @Test
    void rendersTheArchitecturalOverview() {
        String doc = new ArchitectureDocument().render(context("en", narratives()));

        assertThat(doc)
                .startsWith("# Architectural Overview — Orders API\n\n## Summary\n\nLayered architecture.")
                .contains("| Controllers | HTTP adapters |")
                .contains("```mermaid\nflowchart LR\n")
                .contains("```mermaid\nerDiagram\n")
                .contains("| Endpoints per controller (average) | 3.0 |")
                .contains("| Largest service (public methods) | OrderService (2) |")
                .contains("- DTO mapping")
                .contains("| `ENTITY_EXPOSED` | Customer | Entity Customer is used directly by GET /api/orders/{id}/customer. |")
                .contains("### Entity exposed in the API — Customer\n\nLeaks internals.\n\n**Recommendation:** Use a DTO.")
                .contains("## Recommendations\n\n- Add a CustomerResponse DTO");
    }

    @Test
    void rendersTheIndexWithWarnings() {
        String doc = new IndexDocument().render(context("pt-BR", narratives()),
                List.of(new Warning("LLM_MISSING_ENDPOINT", "No text was generated for this endpoint", "OrderController#get")));

        assertThat(doc)
                .startsWith("# Orders API — Documentação da API\n\nGerada pelo apidocs a partir do código-fonte. Textos narrativos: fake:model.")
                .contains("| [Documentação Técnica](technical-documentation.md) | Propósito, stack")
                .contains("| [openapi.yaml](openapi.yaml) | Especificação OpenAPI 3.1. |")
                .contains("| Endpoints | 3 |")
                .contains("## Avisos (1)")
                .contains("| `LLM_MISSING_ENDPOINT` | OrderController#get | No text was generated for this endpoint |");
        assertThat(new IndexDocument().render(context("en", narratives()), List.of())).contains("## Warnings (0)\n\nNo warnings.");
    }

    @Test
    void saysThatDryRunPromptsHoldSourceCodeAndAreGitIgnoredOnlyWhenTheyAreWritten() {
        String english = new IndexDocument().render(context("en", narratives()), List.of(), true);
        String portuguese = new IndexDocument().render(context("pt-BR", narratives()), List.of(), true);

        assertThat(english).contains("| [model.json](model.json) | Raw model extracted from the code. |\n\n"
                + "`prompts/` holds the prompts that would be sent to an LLM; they contain source code, so the folder "
                + "is git-ignored (`prompts/.gitignore`).\n\n## Summary");
        assertThat(portuguese).contains("`prompts/` contém os prompts que seriam enviados a um LLM; eles incluem "
                + "código-fonte, por isso a pasta é ignorada pelo git (`prompts/.gitignore`).");
        assertThat(new IndexDocument().render(context("en", narratives()), List.of())).doesNotContain("prompts/");
    }

    @Test
    void fallsBackToFactsAndPlaceholdersWithoutNarratives() {
        DocumentContext empty = context("en", NarrativeSet.empty());

        assertThat(new ApiReferenceDocument().render(empty))
                .contains("## OrderController\n\nOrder management.")
                .contains("### `POST /api/orders`\n\n> 🔒 Requires: `hasRole('USER')`\n\nCreates an order.")
                .doesNotContain("**Examples**");
        assertThat(new TechnicalDocument().render(empty))
                .contains("## Overview\n\nHandles orders.")
                .contains("## Domain concepts\n\n_Text not generated (no LLM output available for this section)._");
        assertThat(new ArchitectureDocument().render(empty))
                .contains("## Summary\n\n_Text not generated")
                .contains("| `ENTITY_EXPOSED` | Customer |")
                .doesNotContain("### Entity exposed in the API");
    }
}
