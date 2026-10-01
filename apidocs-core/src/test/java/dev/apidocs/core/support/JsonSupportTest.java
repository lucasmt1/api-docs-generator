package dev.apidocs.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class JsonSupportTest {

    @Test
    void writesPrettyJsonWithTypeDiscriminatorAndUnixNewlines() {
        SchemaInfo schema = new SchemaInfo("Money", "x.Money", SchemaKind.OBJECT,
                List.of(new FieldInfo("amount", new ScalarType(ScalarKind.DECIMAL), true, List.of(), "")),
                List.of(), "", false);

        String json = JsonSupport.toPrettyJson(schema);

        assertThat(json).contains("\"type\": \"scalar\"").contains("\"kind\": \"DECIMAL\"");
        assertThat(json).doesNotContain("\r").endsWith("}\n");
    }

    @Test
    void writesEmptyCollectionsCompactly() {
        String json = JsonSupport.toPrettyJson(new TreeMap<>(Map.of("items", List.of(), "extra", Map.of())));

        assertThat(json).isEqualTo("{\n  \"extra\": {},\n  \"items\": []\n}\n");
    }
}
