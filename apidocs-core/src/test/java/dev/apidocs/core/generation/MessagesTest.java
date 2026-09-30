package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void resolvesPortugueseEnglishAndFallback() {
        assertThat(Messages.forLanguage("pt-BR").get("api.responses")).isEqualTo("Respostas");
        assertThat(Messages.forLanguage("en").get("api.responses")).isEqualTo("Responses");
        assertThat(Messages.forLanguage("es").get("api.responses")).isEqualTo("Responses");
        assertThat(Messages.forLanguage("pt-BR").get("api.intro", "3", "1")).isEqualTo("3 endpoints em 1 controllers.");
    }

    @Test
    void bothBundlesDefineTheSameKeys() throws IOException {
        assertThat(load("/i18n/messages_pt.properties").stringPropertyNames())
                .isEqualTo(load("/i18n/messages.properties").stringPropertyNames());
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }
}
