package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.testsupport.ModelFixtures;
import org.junit.jupiter.api.Test;

class MetricsTest {

    private static String averageEndpoints(String language) {
        Messages messages = Messages.forLanguage(language);
        return Metrics.architecture(ModelFixtures.orderApi(), messages).stream()
                .filter(row -> row.get(0).equals(messages.get("metric.avgEndpoints")))
                .map(row -> row.get(1))
                .findFirst().orElseThrow();
    }

    @Test
    void formatsTheAverageWithTheDocumentLanguage() {
        assertThat(averageEndpoints("pt-BR")).isEqualTo("3,0");
        assertThat(averageEndpoints("en")).isEqualTo("3.0");
    }

    @Test
    void exposesTheDocumentLocale() {
        assertThat(Messages.forLanguage("pt-BR").locale().toLanguageTag()).isEqualTo("pt-BR");
        assertThat(Messages.forLanguage("").locale().toLanguageTag()).isEqualTo("en");
    }
}
