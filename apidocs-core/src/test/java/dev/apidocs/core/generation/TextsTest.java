package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.render.MarkdownWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TextsTest {

    private final Messages messages = Messages.forLanguage("en");

    private String section(Optional<List<String>> narrative) {
        MarkdownWriter md = new MarkdownWriter();
        Texts.section(md, messages, narrative, items -> items, md::bullets);
        return md.build();
    }

    @Test
    void sectionShowsThePlaceholderWithoutANarrative() {
        assertThat(section(Optional.empty()))
                .isEqualTo("_Text not generated (no LLM output available for this section)._\n");
    }

    @Test
    void sectionShowsADashWhenTheNarrativeHasNoItems() {
        assertThat(section(Optional.of(List.of()))).isEqualTo("—\n");
    }

    @Test
    void sectionRendersTheItemsOtherwise() {
        assertThat(section(Optional.of(List.of("a", "b")))).isEqualTo("- a\n- b\n");
    }
}
