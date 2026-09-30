package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.apidocs.core.ai.narrative.EndpointNarrative;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordSchemaGeneratorTest {

    @Test
    void generatesStrictObjectSchemasFromRecords() {
        ObjectNode schema = RecordSchemaGenerator.schemaFor(EndpointNarrative.class);

        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(node -> required.add(node.asText()));
        assertThat(required).containsExactly("endpointId", "title", "summary", "description", "businessRules",
                "errorScenarios", "requestExample", "responseExample");
        JsonNode properties = schema.get("properties");
        assertThat(properties.get("title").get("description").asText()).startsWith("Short action title");
        assertThat(properties.get("businessRules").get("items").get("type").asText()).isEqualTo("string");
        JsonNode scenario = properties.get("errorScenarios").get("items");
        assertThat(scenario.get("properties").get("status").get("type").asText()).isEqualTo("integer");
        assertThat(scenario.get("additionalProperties").asBoolean()).isFalse();
    }
}
