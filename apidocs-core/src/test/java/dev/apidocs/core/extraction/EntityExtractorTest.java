package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.EntityField;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.RelationKind;
import dev.apidocs.core.model.Relationship;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityExtractorTest {

    private final TypeIndex index = JavaSnippets.index(
            "package com.x; @MappedSuperclass public abstract class BaseEntity { @Id @GeneratedValue private Long id; }",
            """
            package com.x;
            import java.math.BigDecimal;
            import java.util.List;
            @Entity
            @Table(name = "orders")
            public class Order extends BaseEntity {
                @Column(nullable = false, length = 20) @Enumerated(EnumType.STRING) private Status status;
                @Column(name = "total_amount", unique = true) private BigDecimal total;
                private int version;
                @Transient private String cache;
                @ManyToOne @JoinColumn(name = "customer_id") private Customer customer;
                @OneToMany(mappedBy = "order") private List<OrderItem> items;
                @Embedded private Address shipping;
                @Embedded private Address billing;
            }
            """,
            "package com.x; @Embeddable public class Address { private String street; private String city; }",
            "package com.x; public enum Status { NEW }",
            "package com.x; @Document(collection = \"events\") public class Event { private String id; }");
    private final List<EntityInfo> entities =
            new EntityExtractor(index, new TypeResolver(index, new SchemaRegistry())).extract();

    private EntityInfo entity(String name) {
        return entities.stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private EntityField field(EntityInfo entity, String name) {
        return entity.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void findsEntitiesAndTheirTables() {
        assertThat(entities).extracting(EntityInfo::name).containsExactly("Event", "Order");
        assertThat(entity("Event").table()).isEqualTo("events");
        assertThat(entity("Order").table()).isEqualTo("orders");
    }

    @Test
    void extractsColumnsIncludingInheritedAndEmbeddedFields() {
        EntityInfo order = entity("Order");

        assertThat(order.fields()).extracting(EntityField::name).containsExactly("id", "status", "total", "version",
                "shipping.street", "shipping.city", "billing.street", "billing.city");
        assertThat(field(order, "id").id()).isTrue();
        assertThat(field(order, "id").nullable()).isFalse();
        EntityField status = field(order, "status");
        assertThat(status.type()).isEqualTo(new EnumRef("Status"));
        assertThat(status.nullable()).isFalse();
        assertThat(status.length()).isEqualTo(20);
        EntityField total = field(order, "total");
        assertThat(total.column()).isEqualTo("total_amount");
        assertThat(total.unique()).isTrue();
        assertThat(total.type()).isEqualTo(new ScalarType(ScalarKind.DECIMAL));
        assertThat(field(order, "version").nullable()).isFalse();
    }

    @Test
    void extractsRelationships() {
        assertThat(entity("Order").relationships()).containsExactly(
                new Relationship("customer", RelationKind.MANY_TO_ONE, "Customer", ""),
                new Relationship("items", RelationKind.ONE_TO_MANY, "OrderItem", "order"));
    }

    @Test
    void readsColumnLengthsWrittenWithUnderscoresOrHex() {
        TypeIndex lengthIndex = JavaSnippets.index("""
                package com.x;
                @Entity public class Note {
                    @Column(length = 1_000) private String body;
                    @Column(length = 0x40) private String title;
                }
                """);

        EntityInfo note = new EntityExtractor(lengthIndex, new TypeResolver(lengthIndex, new SchemaRegistry()))
                .extract().get(0);

        assertThat(note.fields()).extracting(EntityField::name, EntityField::length)
                .containsExactly(tuple("body", 1000), tuple("title", 64));
    }

    @Test
    void marksEveryFlattenedColumnOfAnEmbeddedIdAsIdAndNotNullable() {
        TypeIndex compositeIndex = JavaSnippets.index(
                "package com.x; @Entity public class OrderLine { @EmbeddedId private OrderLineId id; private String note; }",
                "package com.x; @Embeddable public class OrderLineId { private Long orderId; private Integer lineNo; }");

        EntityInfo orderLine = new EntityExtractor(compositeIndex,
                new TypeResolver(compositeIndex, new SchemaRegistry())).extract().get(0);

        assertThat(orderLine.fields()).extracting(EntityField::name).containsExactly("id.orderId", "id.lineNo", "note");
        assertThat(orderLine.fields().subList(0, 2)).allSatisfy(column -> {
            assertThat(column.id()).isTrue();
            assertThat(column.nullable()).isFalse();
        });
        EntityField note = field(orderLine, "note");
        assertThat(note.id()).isFalse();
        assertThat(note.nullable()).isTrue();
    }
}
