package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.ai.narrative.ControllerNarrative;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import dev.apidocs.core.ai.narrative.ErrorScenario;
import dev.apidocs.core.ai.narrative.TechnicalDocNarrative;
import dev.apidocs.core.extraction.ModelExtractor;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.source.SourceLimits;
import dev.apidocs.core.testsupport.ModelFixtures;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiDocumentTest {

    private static SwaggerParseResult parse(String yaml) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        return new OpenAPIV3Parser().readContents(yaml, null, options);
    }

    private static NarrativeSet narratives() {
        EndpointNarrative create = new EndpointNarrative("OrderController#create", "Create order", "Creates an order.",
                "Reserves stock and saves the order.", List.of("At least one item"),
                List.of(new ErrorScenario(409, "A product has no stock")), "", "");
        TechnicalDocNarrative technical = new TechnicalDocNarrative("Orders system.", List.of(), List.of(), "", List.of());
        return new NarrativeSet(Map.of("OrderController", new ControllerNarrative("Manages orders.", List.of(create))),
                technical, null);
    }

    @Test
    void producesAValidSpecificationEnrichedWithNarratives() {
        String yaml = new OpenApiDocument().render(ModelFixtures.orderApi(), narratives());

        SwaggerParseResult result = parse(yaml);
        assertThat(result.getMessages()).isEmpty();
        OpenAPI api = result.getOpenAPI();
        assertThat(api.getOpenapi()).isEqualTo("3.1.0");
        assertThat(api.getInfo().getTitle()).isEqualTo("Orders API");
        assertThat(api.getInfo().getDescription()).isEqualTo("Orders system.");
        assertThat(api.getTags().get(0).getDescription()).isEqualTo("Manages orders.");

        Operation create = api.getPaths().get("/api/orders").getPost();
        assertThat(create.getOperationId()).isEqualTo("OrderController_create");
        assertThat(create.getSummary()).isEqualTo("Create order");
        assertThat(create.getResponses()).containsOnlyKeys("201", "400", "404", "409");
        assertThat(create.getResponses().get("409").getDescription()).isEqualTo("A product has no stock");
        assertThat(create.getResponses().get("404").getDescription()).isEqualTo("Not Found: NotFoundException");
        assertThat(create.getExtensions()).containsEntry("x-security-expression", "hasRole('USER')");
        assertThat(api.getPaths().get("/api/orders/{id}/customer").getGet().getDeprecated()).isTrue();
        assertThat(create.getRequestBody().getContent().get("application/json").getSchema().get$ref())
                .isEqualTo("#/components/schemas/CreateOrderRequest");
        assertThat(yaml).doesNotContain("\r");
    }

    @Test
    void mapsValidationConstraintsIntoSchemas() {
        OpenAPI api = parse(new OpenApiDocument().render(ModelFixtures.orderApi(), NarrativeSet.empty())).getOpenAPI();

        Schema<?> request = api.getComponents().getSchemas().get("CreateOrderRequest");
        assertThat(request.getRequired()).containsExactly("customerId", "items");
        Schema<?> items = request.getProperties().get("items");
        assertThat(items.getMinItems()).isEqualTo(1);
        assertThat(items.getMaxItems()).isEqualTo(50);
        Schema<?> orderItem = api.getComponents().getSchemas().get("OrderItemRequest");
        Schema<?> quantity = orderItem.getProperties().get("quantity");
        assertThat(quantity.getMinimum()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(quantity.getMaximum()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(api.getComponents().getSchemas().get("OrderStatus").getEnum()).containsExactly("CREATED", "PAID");
    }

    @Test
    void theSampleApiProducesAValidSpecification() {
        ApiModel model = new ModelExtractor().extract(Path.of("../examples/sample-api"), List.of(), SourceLimits.DEFAULT);

        String yaml = new OpenApiDocument().render(model, NarrativeSet.empty());

        assertThat(parse(yaml).getMessages()).isEmpty();
        assertThat(yaml).contains("/shop/api/orders/{id}/cancel:").contains("PagedModel_ProductResponse:");
    }
}
