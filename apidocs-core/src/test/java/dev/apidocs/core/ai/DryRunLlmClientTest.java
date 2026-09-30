package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.ai.narrative.GlossaryEntry;
import org.junit.jupiter.api.Test;

class DryRunLlmClientTest {

    @Test
    void recordsPromptsWithoutCallingAnyModel() {
        DryRunLlmClient client = new DryRunLlmClient();

        LlmResponse response = client.complete(new LlmRequest("controller-OrderController", "SYS", "USER",
                RecordSchemaGenerator.schemaFor(GlossaryEntry.class), 100));
        client.complete(new LlmRequest("technical-doc", "SYS", "USER 2", null, 100));

        assertThat(response.stopReason()).isEqualTo(LlmStopReason.DRY_RUN);
        assertThat(client.id()).isEqualTo("dry-run");
        assertThat(client.recordedPrompts()).extracting(DryRunLlmClient.RecordedPrompt::fileName)
                .containsExactly("01-controller-OrderController.md", "02-technical-doc.md");
        assertThat(client.recordedPrompts().get(0).toMarkdown())
                .startsWith("# controller-OrderController\n")
                .contains("## System\n\n````text\nSYS\n````")
                .contains("## User\n\n````text\nUSER\n````")
                .contains("## Output schema\n\n```json\n{");
        assertThat(client.recordedPrompts().get(1).toMarkdown()).doesNotContain("Output schema");
    }
}
