package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates();

    private static Map<String, Object> system(boolean withContext) {
        Map<String, Object> context = new HashMap<>();
        context.put("audience", "front-end developers");
        context.put("language", "Portuguese (Brazil) (pt-BR)");
        context.put("hasProjectContext", withContext);
        context.put("description", withContext ? "Shop backend" : "");
        context.put("glossary", withContext ? List.of(Map.of("term", "SKU", "definition", "Stock keeping unit")) : List.of());
        context.put("instructions", "");
        context.put("projectOverview", "- Name: Shop");
        return context;
    }

    @Test
    void rendersTheSystemPromptWithProjectContext() {
        String prompt = templates.render("system", system(true));

        assertThat(prompt)
                .startsWith("You are a senior technical writer documenting a Spring Boot REST API for front-end developers.")
                .contains("Write every natural-language value in Portuguese (Brazil) (pt-BR).")
                .contains("<source_code>")
                .contains("- Description: Shop backend\n- Glossary: SKU = Stock keeping unit\n")
                .doesNotContain("Additional instructions")
                .endsWith("Project overview:\n- Name: Shop");
    }

    @Test
    void omitsTheBackgroundBlockWithoutContext() {
        String prompt = templates.render("system", system(false));

        assertThat(prompt).doesNotContain("Background provided").doesNotContain("\n\n\n");
    }

    @Test
    void rendersTheControllerTask() {
        String prompt = templates.render("controller", Map.of("endpointIds", "A#b, A#c", "endpoints", "E",
                "schemas", "S", "serviceMethods", "M", "exceptionMappings", "X"));

        assertThat(prompt).contains("using the id exactly as written: A#b, A#c")
                .contains("## Endpoints\n\nE\n\n## Data schemas\n\nS")
                .doesNotContain("\r");
    }
}
