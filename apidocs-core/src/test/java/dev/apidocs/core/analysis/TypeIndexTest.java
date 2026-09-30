package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeIndexTest {

    private final List<ParsedUnit> units = JavaSnippets.parse(
            "package com.x.dto; public record OrderDto(Long id) { public record Line(int q) {} }",
            "package com.x.service; import com.x.dto.OrderDto; import com.x.model.*; public class OrderService {}",
            "package com.x.model; public class Order {}",
            "package com.x.service; class Helper {}",
            "package com.x.other; public class Page<T> {}",
            "package com.x.web; import org.springframework.data.domain.Page; public class Web {}");
    private final TypeIndex index = TypeIndex.build(units);
    private final ParsedUnit dto = units.get(0);
    private final ParsedUnit service = units.get(1);
    private final ParsedUnit web = units.get(5);

    private String resolve(String name, ParsedUnit context) {
        return index.resolve(name, context).map(TypeIndex.IndexedType::qualifiedName).orElse("<none>");
    }

    @Test
    void indexesTopLevelAndNestedTypes() {
        assertThat(index.all()).extracting(TypeIndex.IndexedType::qualifiedName).containsExactly(
                "com.x.dto.OrderDto", "com.x.dto.OrderDto.Line", "com.x.model.Order", "com.x.other.Page",
                "com.x.service.Helper", "com.x.service.OrderService", "com.x.web.Web");
        assertThat(index.bySimpleName("Line")).hasSize(1);
    }

    @Test
    void resolvesThroughImportsPackagesAndNesting() {
        assertThat(resolve("OrderDto", service)).isEqualTo("com.x.dto.OrderDto");
        assertThat(resolve("OrderDto.Line", service)).isEqualTo("com.x.dto.OrderDto.Line");
        assertThat(resolve("Order", service)).isEqualTo("com.x.model.Order");
        assertThat(resolve("Helper", service)).isEqualTo("com.x.service.Helper");
        assertThat(resolve("Line", dto)).isEqualTo("com.x.dto.OrderDto.Line");
        assertThat(resolve("com.x.model.Order", web)).isEqualTo("com.x.model.Order");
        assertThat(resolve("List<OrderDto>", service)).isEqualTo("<none>");
        assertThat(resolve("OrderDto[]", service)).isEqualTo("com.x.dto.OrderDto");
    }

    @Test
    void explicitExternalImportsShadowProjectTypes() {
        assertThat(resolve("Page", web)).isEqualTo("<none>");
        assertThat(resolve("Page", service)).isEqualTo("com.x.other.Page");
        assertThat(resolve("Unknown", service)).isEqualTo("<none>");
    }
}
