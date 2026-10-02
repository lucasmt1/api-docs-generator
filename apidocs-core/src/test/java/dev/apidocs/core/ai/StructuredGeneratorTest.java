package dev.apidocs.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.ai.narrative.GlossaryEntry;
import dev.apidocs.core.ai.narrative.RuleGroup;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.FakeLlmClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.Test;

class StructuredGeneratorTest {

    private static final String VALID = "{\"term\": \"SKU\", \"definition\": \"Stock keeping unit\"}";

    @Test
    void parsesAValidAnswerOnTheFirstTry() {
        FakeLlmClient llm = FakeLlmClient.replying(VALID);

        StructuredGenerator.Result<GlossaryEntry> result =
                new StructuredGenerator(llm).generate("glossary", "system", "user", GlossaryEntry.class, 1000);

        assertThat(result.value()).contains(new GlossaryEntry("SKU", "Stock keeping unit"));
        assertThat(result.calls()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
        assertThat(llm.requests().get(0).purpose()).isEqualTo("glossary");
        assertThat(llm.requests().get(0).outputSchema().get("required")).hasSize(2);
        assertThat(llm.requests().get(0).maxOutputTokens()).isEqualTo(1000);
    }

    @Test
    void stripsMarkdownFencesAndSurroundingText() {
        FakeLlmClient llm = FakeLlmClient.replying("Here it is:\n```json\n" + VALID + "\n```\nDone.");

        assertThat(new StructuredGenerator(llm).generate("g", "s", "u", GlossaryEntry.class, 100).value())
                .contains(new GlossaryEntry("SKU", "Stock keeping unit"));
    }

    @Test
    void retriesOnceExplainingTheProblem() {
        FakeLlmClient llm = FakeLlmClient.replying("not json", VALID);

        StructuredGenerator.Result<GlossaryEntry> result =
                new StructuredGenerator(llm).generate("g", "s", "user prompt", GlossaryEntry.class, 100);

        assertThat(result.value()).isPresent();
        assertThat(result.calls()).isEqualTo(2);
        assertThat(result.usage()).isEqualTo(new TokenUsage(200, 100, 0));
        assertThat(llm.requests().get(1).userPrompt())
                .startsWith("user prompt")
                .contains("IMPORTANT: your previous answer could not be used");
    }

    @Test
    void treatsMissingFieldsAsInvalid() {
        FakeLlmClient llm = FakeLlmClient.replying("{\"domain\": \"Orders\"}", "{\"domain\": \"Orders\", \"rules\": []}");

        StructuredGenerator.Result<RuleGroup> result =
                new StructuredGenerator(llm).generate("g", "s", "u", RuleGroup.class, 100);

        assertThat(result.calls()).isEqualTo(2);
        assertThat(llm.requests().get(1).userPrompt()).contains("missing fields [rules]");
        assertThat(result.value()).isPresent();
    }

    @Test
    void givesUpAfterTwoInvalidAnswers() {
        StructuredGenerator.Result<GlossaryEntry> result = new StructuredGenerator(FakeLlmClient.replying("x", "y"))
                .generate("controller-A", "s", "u", GlossaryEntry.class, 100);

        assertThat(result.value()).isEmpty();
        assertThat(result.warnings()).extracting(Warning::code, Warning::location)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("LLM_OUTPUT_INVALID", "controller-A"));
    }

    @Test
    void turnsProviderFailuresRefusalsAndDryRunsIntoResults() {
        FakeLlmClient failing = new FakeLlmClient(request -> {
            throw new LlmException("HTTP 500", true);
        });
        FakeLlmClient refusing = new FakeLlmClient(request ->
                new LlmResponse("", LlmStopReason.REFUSAL, TokenUsage.ZERO, "m"));
        FakeLlmClient dryRun = new FakeLlmClient(request ->
                new LlmResponse("", LlmStopReason.DRY_RUN, TokenUsage.ZERO, "dry-run"));

        assertThat(new StructuredGenerator(failing).generate("p", "s", "u", GlossaryEntry.class, 1).warnings())
                .extracting(Warning::code).containsExactly("LLM_CALL_FAILED");
        assertThat(new StructuredGenerator(refusing).generate("p", "s", "u", GlossaryEntry.class, 1).warnings())
                .extracting(Warning::code).containsExactly("LLM_REFUSED");
        StructuredGenerator.Result<GlossaryEntry> dry =
                new StructuredGenerator(dryRun).generate("p", "s", "u", GlossaryEntry.class, 1);
        assertThat(dry.value()).isEmpty();
        assertThat(dry.warnings()).isEmpty();
    }

    @Test
    void persistsOnlyTheSummaryOfAProviderFailureAndLogsItsDetailToTheConsole() {
        FakeLlmClient detailed = new FakeLlmClient(request -> {
            throw new LlmException("HTTP 429 from the LLM provider",
                    "api.example.test: Quota exceeded for organization org-AbC123", true);
        });
        FakeLlmClient bare = new FakeLlmClient(request -> {
            throw new LlmException("HTTP 500 from the LLM provider", true);
        });
        Logger logger = Logger.getLogger(StructuredGenerator.class.getName());
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            assertThat(new StructuredGenerator(detailed).generate("controller-A", "s", "u", GlossaryEntry.class, 1)
                    .warnings())
                    .containsExactly(new Warning("LLM_CALL_FAILED", "HTTP 429 from the LLM provider", "controller-A"));
            assertThat(new StructuredGenerator(bare).generate("technical-doc", "s", "u", GlossaryEntry.class, 1)
                    .warnings())
                    .containsExactly(new Warning("LLM_CALL_FAILED", "HTTP 500 from the LLM provider", "technical-doc"));
        } finally {
            logger.removeHandler(capture);
        }

        assertThat(records).allSatisfy(record -> assertThat(record.getLevel()).isEqualTo(Level.WARNING))
                .extracting(record -> new SimpleFormatter().formatMessage(record))
                .containsExactly(
                        "LLM call failed for controller-A: api.example.test: Quota exceeded for organization org-AbC123",
                        "LLM call failed for technical-doc: HTTP 500 from the LLM provider");
    }
}
