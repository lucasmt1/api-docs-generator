package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.testsupport.ModelFixtures;
import org.junit.jupiter.api.Test;

class ExampleSanitizerTest {

    private final ExampleSanitizer sanitizer = new ExampleSanitizer(ModelFixtures.orderApi().schemasByName());

    @Test
    void keepsOnlyDocumentedFieldsInSchemaOrder() {
        ExampleSanitizer.Result result = sanitizer.sanitize(
                "{\"items\": [{\"quantity\": 2, \"productId\": 7, \"color\": \"red\"}], \"customerId\": 1, \"extra\": true}",
                new ObjectRef("CreateOrderRequest"));

        assertThat(result.invalid()).isFalse();
        assertThat(result.json()).isEqualTo("""
                {
                  "customerId": 1,
                  "items": [
                    {
                      "productId": 7,
                      "quantity": 2
                    }
                  ]
                }""");
    }

    @Test
    void acceptsMarkdownFencesAndArrays() {
        ExampleSanitizer.Result result = sanitizer.sanitize("```json\n[{\"id\": 1, \"x\": 2}]\n```",
                new ArrayOf(new ObjectRef("OrderResponse")));

        assertThat(result.json()).isEqualTo("[\n  {\n    \"id\": 1\n  }\n]");
    }

    @Test
    void rejectsInvalidOrUnrelatedExamples() {
        assertThat(sanitizer.sanitize("not json", new ObjectRef("OrderResponse")).invalid()).isTrue();
        assertThat(sanitizer.sanitize("{\"foo\": 1}", new ObjectRef("CreateOrderRequest")).invalid()).isTrue();
        assertThat(sanitizer.sanitize("  ", new ObjectRef("OrderResponse"))).isEqualTo(new ExampleSanitizer.Result("", false));
        assertThat(sanitizer.sanitize("{\"a\": 1}", null)).isEqualTo(new ExampleSanitizer.Result("", false));
    }

    @Test
    void rejectsAnObjectWhereAnArrayIsExpected() {
        ArrayOf orders = new ArrayOf(new ObjectRef("OrderResponse"));

        assertThat(sanitizer.sanitize("{\"content\": [{\"id\": 1, \"x\": 2}], \"totalPages\": 3}", orders))
                .isEqualTo(new ExampleSanitizer.Result("", true));
    }

    @Test
    void rejectsAnArrayWhereAnObjectIsExpected() {
        assertThat(sanitizer.sanitize("[{\"customerId\": 1, \"hack\": true}]", new ObjectRef("CreateOrderRequest")))
                .isEqualTo(new ExampleSanitizer.Result("", true));
    }

    @Test
    void rejectsNullAndScalarsWhereAStructureIsExpected() {
        ObjectRef order = new ObjectRef("OrderResponse");

        assertThat(sanitizer.sanitize("null", order)).isEqualTo(new ExampleSanitizer.Result("", true));
        assertThat(sanitizer.sanitize("null", new ArrayOf(order))).isEqualTo(new ExampleSanitizer.Result("", true));
        assertThat(sanitizer.sanitize("\"just text\"", order)).isEqualTo(new ExampleSanitizer.Result("", true));
        assertThat(sanitizer.sanitize("42", new ArrayOf(order))).isEqualTo(new ExampleSanitizer.Result("", true));
    }

    @Test
    void rejectsAnArrayWhoseElementsAllLoseEveryField() {
        ArrayOf orders = new ArrayOf(new ObjectRef("OrderResponse"));

        assertThat(sanitizer.sanitize("[{\"x\": 1}, {\"y\": 2}]", orders))
                .isEqualTo(new ExampleSanitizer.Result("", true));
        assertThat(sanitizer.sanitize("[\"a\", 3]", orders)).isEqualTo(new ExampleSanitizer.Result("", true));
    }

    @Test
    void dropsNestedFieldsWhoseShapeContradictsTheSchema() {
        ObjectRef request = new ObjectRef("CreateOrderRequest");

        assertThat(sanitizer.sanitize("{\"customerId\": 1, \"items\": {\"productId\": 7}}", request).json())
                .isEqualTo("{\n  \"customerId\": 1\n}");
        assertThat(sanitizer.sanitize("{\"customerId\": 1, \"items\": null}", request).json())
                .isEqualTo("{\n  \"customerId\": 1\n}");
        assertThat(sanitizer.sanitize("{\"customerId\": 1, \"items\": [{\"color\": \"red\"}]}", request).json())
                .isEqualTo("{\n  \"customerId\": 1\n}");
        assertThat(sanitizer.sanitize("{\"customerId\": 1, \"items\": [\"a\"]}", request).invalid()).isFalse();
    }

    @Test
    void dropsMismatchedElementsButKeepsTheValidOnes() {
        ExampleSanitizer.Result result = sanitizer.sanitize(
                "{\"customerId\": 1, \"items\": [\"oops\", {\"productId\": 7, \"color\": \"red\"}, [1]]}",
                new ObjectRef("CreateOrderRequest"));

        assertThat(result.invalid()).isFalse();
        assertThat(result.json()).isEqualTo("""
                {
                  "customerId": 1,
                  "items": [
                    {
                      "productId": 7
                    }
                  ]
                }""");
    }

    @Test
    void keepsAnEmptyArrayTheModelWroteOnPurpose() {
        ExampleSanitizer.Result result = sanitizer.sanitize("{\"customerId\": 1, \"items\": []}",
                new ObjectRef("CreateOrderRequest"));

        assertThat(result.invalid()).isFalse();
        assertThat(result.json()).contains("\"customerId\": 1").contains("\"items\": [");
    }
}
