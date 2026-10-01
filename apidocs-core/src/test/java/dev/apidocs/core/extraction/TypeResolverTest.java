package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.Type;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TypeResolverTest {

    private final List<ParsedUnit> units = JavaSnippets.parse(
            """
            package com.x;
            import java.util.*;
            import org.springframework.data.domain.Page;
            public class Holder<T> {
                int a; String b; List<Item> c; Map<String, Integer> d; Optional<Item> e; Page<Item> f; Status g;
                byte[] h; Object i; ApiResponse<Item> j; T k; java.time.Instant l; Set<Status> m;
                java.math.BigDecimal n; Number o;
            }
            """,
            "package com.x; public record Item(String name) {}",
            "package com.x; public enum Status { A, B }",
            "package com.x; public record ApiResponse<T>(T data, String message) {}");
    private final TypeIndex index = TypeIndex.build(units);
    private final SchemaRegistry registry = new SchemaRegistry();
    private final TypeResolver resolver = new TypeResolver(index, registry);
    private final ParsedUnit holder = units.get(0);

    private Type declaredType(String fieldName) {
        return holder.cu().findFirst(ClassOrInterfaceDeclaration.class).orElseThrow()
                .getFieldByName(fieldName).orElseThrow().getVariable(0).getType();
    }

    private TypeRef field(String fieldName) {
        return resolver.resolve(declaredType(fieldName), holder, Map.of("T", new ScalarType(ScalarKind.STRING)));
    }

    private List<SchemaRegistry.SchemaRequest> drain() {
        List<SchemaRegistry.SchemaRequest> requests = new ArrayList<>();
        Optional<SchemaRegistry.SchemaRequest> next;
        while ((next = registry.next()).isPresent()) {
            requests.add(next.get());
        }
        return requests;
    }

    @Test
    void mapsJavaTypesToDocumentedTypes() {
        assertThat(field("a")).isEqualTo(new ScalarType(ScalarKind.INTEGER));
        assertThat(field("b")).isEqualTo(new ScalarType(ScalarKind.STRING));
        assertThat(field("c")).isEqualTo(new ArrayOf(new ObjectRef("Item")));
        assertThat(field("d")).isEqualTo(new MapOf(new ScalarType(ScalarKind.INTEGER)));
        assertThat(field("e")).isEqualTo(new ObjectRef("Item"));
        assertThat(field("g")).isEqualTo(new EnumRef("Status"));
        assertThat(field("h")).isEqualTo(new ScalarType(ScalarKind.BINARY));
        assertThat(field("i")).isEqualTo(new OpaqueType("Object"));
        assertThat(field("k")).isEqualTo(new ScalarType(ScalarKind.STRING));
        assertThat(field("l")).isEqualTo(new ScalarType(ScalarKind.DATE_TIME));
        assertThat(field("m")).isEqualTo(new ArrayOf(new EnumRef("Status")));
        assertThat(field("n")).isEqualTo(new ScalarType(ScalarKind.DECIMAL));
        assertThat(field("o")).isEqualTo(new ScalarType(ScalarKind.NUMBER));
    }

    @Test
    void requestsSchemasForProjectTypesWithBoundTypeArguments() {
        assertThat(field("j")).isEqualTo(new ObjectRef("ApiResponse_Item"));

        List<SchemaRegistry.SchemaRequest> requests = drain();

        assertThat(requests).extracting(SchemaRegistry.SchemaRequest::schemaName)
                .containsExactly("Item", "ApiResponse_Item");
        assertThat(requests.get(1).typeArguments()).containsEntry("T", new ObjectRef("Item"));
    }

    @Test
    void describesSpringPagesWithSyntheticSchemas() {
        assertThat(field("f")).isEqualTo(new ObjectRef("PagedModel_Item"));

        assertThat(registry.synthetic()).extracting(SchemaInfo::name).containsExactly("PageMetadata", "PagedModel_Item");
        SchemaInfo page = registry.synthetic().stream()
                .filter(s -> s.name().equals("PagedModel_Item")).findFirst().orElseThrow();
        assertThat(page.fields()).extracting(FieldInfo::name).containsExactly("content", "page");
        assertThat(page.fields().get(0).type()).isEqualTo(new ArrayOf(new ObjectRef("Item")));
    }

    @Test
    void resolvesWithoutRegisteringSchemas() {
        assertThat(resolver.resolveWithoutSchemas(declaredType("c"), holder)).isEqualTo(new ArrayOf(new ObjectRef("Item")));
        assertThat(resolver.resolveWithoutSchemas(declaredType("g"), holder)).isEqualTo(new EnumRef("Status"));
        assertThat(registry.next()).isEmpty();
    }

    @Test
    void renamesCollidingSchemaNames() {
        TypeIndex twoTypes = JavaSnippets.index("package com.a; public class Dup {}", "package com.b; public class Dup {}");
        SchemaRegistry names = new SchemaRegistry();
        List<TypeIndex.IndexedType> dups = List.copyOf(twoTypes.all());

        assertThat(names.request(dups.get(0), List.of())).isEqualTo("Dup");
        assertThat(names.request(dups.get(1), List.of())).isEqualTo("com_b_Dup");
        assertThat(names.request(dups.get(0), List.of())).isEqualTo("Dup");
        assertThat(names.warnings()).extracting(Warning::code).containsExactly("SCHEMA_NAME_COLLISION");
    }
}
