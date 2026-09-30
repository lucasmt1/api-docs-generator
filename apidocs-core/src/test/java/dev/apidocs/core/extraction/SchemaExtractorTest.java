package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaExtractorTest {

    private static List<SchemaInfo> extract(String rootType, Set<String> entities, String... sources) {
        TypeIndex index = JavaSnippets.index(sources);
        SchemaRegistry registry = new SchemaRegistry();
        TypeResolver resolver = new TypeResolver(index, registry);
        registry.request(index.bySimpleName(rootType).get(0), List.of());
        return new SchemaExtractor(index, resolver, registry, entities).extractAll();
    }

    private static SchemaInfo schema(List<SchemaInfo> schemas, String name) {
        return schemas.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void extractsRecordComponentsWithValidation() {
        List<SchemaInfo> schemas = extract("CreateOrderRequest", Set.of(),
                """
                package com.x;
                import jakarta.validation.constraints.*;
                import java.util.List;
                /** Items to buy. */
                public record CreateOrderRequest(
                        @NotNull Long customerId,
                        @NotEmpty @Size(max = 50) List<OrderItemRequest> items,
                        String note) {}
                """,
                """
                package com.x;
                import jakarta.validation.constraints.*;
                public record OrderItemRequest(@NotNull Long productId, @Min(1) @Max(10) int quantity) {}
                """);

        assertThat(schemas).extracting(SchemaInfo::name).containsExactly("CreateOrderRequest", "OrderItemRequest");
        SchemaInfo order = schema(schemas, "CreateOrderRequest");
        assertThat(order.description()).isEqualTo("Items to buy.");
        assertThat(order.fields()).extracting(FieldInfo::name).containsExactly("customerId", "items", "note");
        FieldInfo items = order.fields().get(1);
        assertThat(items.type()).isEqualTo(new ArrayOf(new ObjectRef("OrderItemRequest")));
        assertThat(items.required()).isTrue();
        assertThat(items.constraints()).extracting(Constraint::describe).containsExactly("@NotEmpty", "@Size(max=50)");
        assertThat(order.fields().get(2).required()).isFalse();
        FieldInfo quantity = schema(schemas, "OrderItemRequest").fields().get(1);
        assertThat(quantity.required()).isTrue();
        assertThat(quantity.constraints()).extracting(Constraint::describe).containsExactly("@Min(value=1)", "@Max(value=10)");
    }

    @Test
    void extractsClassFieldsIncludingInheritedOnesAndJacksonAnnotations() {
        List<SchemaInfo> schemas = extract("CustomerDto", Set.of(),
                "package com.x; public abstract class Base { protected Long id; }",
                """
                package com.x;
                import com.fasterxml.jackson.annotation.*;
                /** A customer. */
                public class CustomerDto extends Base {
                    private static final long serialVersionUID = 1L;
                    /** Full name. */
                    @JsonProperty("full_name") private String name;
                    @JsonIgnore private String password;
                    private transient String cache;
                }
                """);

        SchemaInfo customer = schema(schemas, "CustomerDto");
        assertThat(customer.description()).isEqualTo("A customer.");
        assertThat(customer.fields()).extracting(FieldInfo::name).containsExactly("id", "full_name");
        assertThat(customer.fields().get(1).description()).isEqualTo("Full name.");
    }

    @Test
    void extractsEnumValues() {
        List<SchemaInfo> schemas = extract("Status", Set.of(),
                "package com.x; /** Order status. */ public enum Status { CREATED, PAID }");

        assertThat(schemas).singleElement().satisfies(s -> {
            assertThat(s.kind()).isEqualTo(SchemaKind.ENUM);
            assertThat(s.enumValues()).containsExactly("CREATED", "PAID");
            assertThat(s.description()).isEqualTo("Order status.");
        });
    }

    @Test
    void substitutesGenericTypeArguments() {
        List<SchemaInfo> schemas = extract("Envelope", Set.of(),
                "package com.x; public record ApiResponse<T>(T data, String message) {}",
                "package com.x; public record Payload(int x) {}",
                "package com.x; public record Envelope(ApiResponse<Payload> response) {}");

        assertThat(schemas).extracting(SchemaInfo::name).containsExactly("ApiResponse_Payload", "Envelope", "Payload");
        assertThat(schema(schemas, "ApiResponse_Payload").fields().get(0).type()).isEqualTo(new ObjectRef("Payload"));
    }

    @Test
    void marksEntitiesAndHandlesSelfReferences() {
        List<SchemaInfo> schemas = extract("Node", Set.of("com.x.Node"),
                "package com.x; import java.util.List; public class Node { String name; List<Node> children; }");

        assertThat(schemas).singleElement().satisfies(s -> {
            assertThat(s.entity()).isTrue();
            assertThat(s.fields().get(1).type()).isEqualTo(new ArrayOf(new ObjectRef("Node")));
        });
    }

    @Test
    void includesSyntheticPageSchemas() {
        List<SchemaInfo> schemas = extract("Wrapper", Set.of(),
                "package com.x; import org.springframework.data.domain.Page; public record Wrapper(Page<Item> page) {}",
                "package com.x; public record Item(String name) {}");

        assertThat(schemas).extracting(SchemaInfo::name)
                .containsExactly("Item", "PageMetadata", "PagedModel_Item", "Wrapper");
    }

    @Test
    void stopsAtTheSchemaLimitOnUnboundedGenericNesting() {
        TypeIndex index = JavaSnippets.index("package com.x; public class Node<T> { Node<Node<T>> next; }");
        SchemaRegistry registry = new SchemaRegistry();
        TypeResolver resolver = new TypeResolver(index, registry);
        registry.request(index.bySimpleName("Node").get(0), List.of());

        List<SchemaInfo> schemas = new SchemaExtractor(index, resolver, registry, Set.of(), 5).extractAll();

        assertThat(schemas).hasSize(5);
        assertThat(registry.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("SCHEMA_LIMIT_REACHED");
            assertThat(warning.message()).isEqualTo("Schema extraction stopped after 5 schemas");
            assertThat(warning.location()).isEmpty();
        });
    }

    @Test
    void doesNotWarnWhenTheSchemaCountEqualsTheLimit() {
        TypeIndex index = JavaSnippets.index(
                "package com.x; public record Parent(Child child) {}",
                "package com.x; public record Child(String name) {}");
        SchemaRegistry registry = new SchemaRegistry();
        TypeResolver resolver = new TypeResolver(index, registry);
        registry.request(index.bySimpleName("Parent").get(0), List.of());

        List<SchemaInfo> schemas = new SchemaExtractor(index, resolver, registry, Set.of(), 2).extractAll();

        assertThat(schemas).extracting(SchemaInfo::name).containsExactly("Child", "Parent");
        assertThat(registry.warnings()).isEmpty();
    }

    @Test
    void defaultSchemaLimitIsOneThousand() {
        assertThat(SchemaExtractor.MAX_SCHEMAS).isEqualTo(1000);
    }
}
