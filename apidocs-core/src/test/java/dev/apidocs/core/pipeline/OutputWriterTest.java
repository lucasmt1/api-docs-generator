package dev.apidocs.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.ConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputWriterTest {

    @TempDir
    Path dir;

    private final OutputWriter writer = new OutputWriter();

    @Test
    void writesFilesWithUnixNewlines() throws IOException {
        Path out = dir.resolve("docs");

        writer.writeAtomically(out, Map.of("model.json", "{}\r\n", "prompts/01-a.md", "# A\r\n"));

        assertThat(Files.readString(out.resolve("model.json"))).isEqualTo("{}\n");
        assertThat(Files.readString(out.resolve("prompts/01-a.md"))).isEqualTo("# A\n");
    }

    @Test
    void replacesAPreviouslyGeneratedFolder() throws IOException {
        Path out = dir.resolve("docs");
        writer.writeAtomically(out, Map.of("generation-report.json", "{}", "model.json", "{}", "README.md", "old"));

        writer.writeAtomically(out, Map.of("generation-report.json", "{}", "model.json", "{\"v\":2}"));

        assertThat(out.resolve("README.md")).doesNotExist();
        assertThat(Files.readString(out.resolve("model.json"))).isEqualTo("{\"v\":2}");
        try (Stream<Path> siblings = Files.list(dir)) {
            assertThat(siblings.map(p -> p.getFileName().toString())).containsExactly("docs");
        }
    }

    @Test
    void replacesAPreviousOutputThatIncludesPrompts() throws IOException {
        Path out = dir.resolve("docs");
        writer.writeAtomically(out, Map.ofEntries(
                Map.entry("README.md", "old"), Map.entry("technical-documentation.md", "old"),
                Map.entry("api-reference.md", "old"), Map.entry("architecture-overview.md", "old"),
                Map.entry("openapi.yaml", "old"), Map.entry("model.json", "{}"),
                Map.entry("generation-report.json", "{}"), Map.entry("prompts/01-controller-A.md", "old")));

        writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}"));

        try (Stream<Path> entries = Files.list(out)) {
            assertThat(entries.map(p -> p.getFileName().toString())).containsExactly("generation-report.json");
        }
    }

    @Test
    void replacesAPreviousOutputDecoratedWithOsMetadataFiles() throws IOException {
        Path out = dir.resolve("docs");
        writer.writeAtomically(out, Map.of("generation-report.json", "{}", "prompts/01-a.md", "old"));
        Files.writeString(out.resolve(".DS_Store"), "finder");
        Files.writeString(out.resolve("Thumbs.db"), "explorer");
        Files.writeString(out.resolve("prompts/desktop.ini"), "explorer");

        writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}"));

        try (Stream<Path> entries = Files.list(out)) {
            assertThat(entries.map(p -> p.getFileName().toString())).containsExactly("generation-report.json");
        }
        assertSiblings("docs");
    }

    @Test
    void osMetadataFilesDoNotHideAForeignFile() throws IOException {
        Path out = Files.createDirectories(dir.resolve("mine"));
        Files.writeString(out.resolve("generation-report.json"), "{}");
        Files.writeString(out.resolve(".DS_Store"), "finder");
        Files.writeString(out.resolve("notes.txt"), "important");

        assertThatThrownBy(() -> writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("notes.txt");
        assertThat(Files.readString(out.resolve("notes.txt"))).isEqualTo("important");
    }

    @Test
    void refusesAFolderThatOnlyHoldsAStrayModelJson() throws IOException {
        Path out = Files.createDirectories(dir.resolve("mine"));
        Files.writeString(out.resolve("model.json"), "not ours");

        assertThatThrownBy(() -> writer.writeAtomically(out, Map.of("generation-report.json", "{}")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("not empty")
                .hasMessageContaining("generation-report.json");
        assertThat(Files.readString(out.resolve("model.json"))).isEqualTo("not ours");
        assertSiblings("mine");
    }

    @Test
    void refusesAFolderWithForeignFilesNextToTheReport() throws IOException {
        Path out = Files.createDirectories(dir.resolve("mine"));
        Files.writeString(out.resolve("generation-report.json"), "{}");
        Files.writeString(out.resolve("notes.txt"), "important");

        assertThatThrownBy(() -> writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("not empty")
                .hasMessageContaining("notes.txt");
        assertThat(Files.readString(out.resolve("notes.txt"))).isEqualTo("important");
        assertThat(Files.readString(out.resolve("generation-report.json"))).isEqualTo("{}");
        assertSiblings("mine");
    }

    @Test
    void refusesAFolderWhosePromptsHoldForeignFiles() throws IOException {
        Path out = Files.createDirectories(dir.resolve("mine/prompts"));
        Files.writeString(out.getParent().resolve("generation-report.json"), "{}");
        Files.writeString(out.resolve("my-notes.txt"), "important");

        assertThatThrownBy(() -> writer.writeAtomically(out.getParent(), Map.of("generation-report.json", "{}")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("prompts");
        assertThat(Files.readString(out.resolve("my-notes.txt"))).isEqualTo("important");
    }

    @Test
    void refusesAFolderWhoseExpectedNameIsOfTheWrongKind() throws IOException {
        Path out = Files.createDirectories(dir.resolve("mine"));
        Files.writeString(out.resolve("generation-report.json"), "{}");
        Files.createDirectories(out.resolve("README.md"));

        assertThatThrownBy(() -> writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("README.md");
        assertThat(out.resolve("README.md")).isDirectory();
    }

    @Test
    void aBackupThatCannotBeDeletedDoesNotFailAnAlreadyCompletedSwap() throws IOException {
        Path out = dir.resolve("docs");
        writer.writeAtomically(out, Map.of("generation-report.json", "{}", "README.md", "old"));
        OutputWriter locked = new OutputWriter(path -> {
            throw new IOException("locked by an indexer");
        });

        locked.writeAtomically(out, Map.of("generation-report.json", "{\"v\":2}"));

        assertThat(Files.readString(out.resolve("generation-report.json"))).isEqualTo("{\"v\":2}");
        assertThat(out.resolve("README.md")).doesNotExist();
        try (Stream<Path> siblings = Files.list(dir)) {
            assertThat(siblings.map(p -> p.getFileName().toString()))
                    .hasSize(2).contains("docs").anyMatch(name -> name.startsWith(".docs.apidocs-old-"));
        }

        writer.writeAtomically(out, Map.of("generation-report.json", "{\"v\":3}"));

        assertSiblings("docs");
    }

    private void assertSiblings(String... names) throws IOException {
        try (Stream<Path> siblings = Files.list(dir)) {
            assertThat(siblings.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder(names);
        }
    }

    @Test
    void rejectsFileNamesEscapingTheFolder() {
        assertThatThrownBy(() -> writer.writeAtomically(dir.resolve("docs"), Map.of("../evil.md", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("evil.md")).doesNotExist();
    }

    @Test
    void cleansStaleTemporaryFolders() throws IOException {
        Files.createDirectories(dir.resolve(".docs.apidocs-tmp-123/x"));

        writer.writeAtomically(dir.resolve("docs"), Map.of("model.json", "{}"));

        assertThat(dir.resolve(".docs.apidocs-tmp-123")).doesNotExist();
    }
}
