package dev.apidocs.core.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.model.Warning;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceLoaderTest {

    @TempDir
    Path dir;

    private void write(String relative, String content) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Creates a symbolic link, skipping the test when the platform or the user is not allowed to. */
    private void symlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("Symbolic links are not available here: " + e);
        }
    }

    @Test
    void loadsMainJavaSourcesSortedAndSkipsTestsBuildOutputAndExcludes() throws IOException {
        write("src/main/java/com/x/B.java", "class B {}");
        write("src/main/java/com/x/A.java", "class A {}");
        write("module/src/main/java/com/y/C.java", "class C {}");
        write("src/test/java/com/x/ATest.java", "class ATest {}");
        write("target/generated/src/main/java/G.java", "class G {}");
        write("src/main/java/com/x/legacy/Old.java", "class Old {}");
        write("src/main/resources/application.yml", "a: b");

        LoadedSources sources = new SourceLoader().load(dir, List.of("**/legacy/**"), SourceLimits.DEFAULT);

        assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly(
                "module/src/main/java/com/y/C.java",
                "src/main/java/com/x/A.java",
                "src/main/java/com/x/B.java");
        assertThat(sources.javaFiles().get(1).content()).isEqualTo("class A {}");
        assertThat(sources.warnings()).isEmpty();
    }

    @Test
    void keepsPackagesNamedLikeBuildOutputDirectoriesInsideSrc() throws IOException {
        write("src/main/java/com/acme/build/Tool.java", "class Tool {}");
        write("src/main/java/com/acme/out/X.java", "class X {}");
        write("src/main/java/com/acme/target/Y.java", "class Y {}");
        write("target/generated/src/main/java/G.java", "class G {}");
        write("module/build/generated/src/main/java/H.java", "class H {}");
        write("out/production/src/main/java/I.java", "class I {}");

        LoadedSources sources = new SourceLoader().load(dir, List.of(), SourceLimits.DEFAULT);

        assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly(
                "src/main/java/com/acme/build/Tool.java",
                "src/main/java/com/acme/out/X.java",
                "src/main/java/com/acme/target/Y.java");
        assertThat(sources.warnings()).isEmpty();
    }

    @Test
    void neverFollowsSymbolicLinks() throws IOException {
        Files.writeString(dir.resolve("secret.txt"), "top secret");
        write("src/main/java/Real.java", "class Real {}");
        Files.createDirectories(dir.resolve("hidden"));
        Files.writeString(dir.resolve("hidden/Hidden.java"), "class Hidden {}");
        symlink(dir.resolve("src/main/java/Leak.java"), dir.resolve("secret.txt"));
        symlink(dir.resolve("src/main/java/linked"), dir.resolve("hidden"));

        LoadedSources sources = new SourceLoader().load(dir, List.of(), SourceLimits.DEFAULT);

        assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly("src/main/java/Real.java");
        assertThat(sources.warnings()).isEmpty();
    }

    @Test
    void walksASymlinkedProjectRootButStillNeverFollowsLinksBelowIt() throws IOException {
        write("real/src/main/java/A.java", "class A {}");
        Files.writeString(dir.resolve("secret.txt"), "top secret");
        symlink(dir.resolve("real/src/main/java/Leak.java"), dir.resolve("secret.txt"));
        symlink(dir.resolve("link"), dir.resolve("real"));

        LoadedSources sources = new SourceLoader().load(dir.resolve("link"), List.of(), SourceLimits.DEFAULT);

        assertThat(sources.root()).isEqualTo(dir.resolve("real").toRealPath());
        assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly("src/main/java/A.java");
        assertThat(sources.warnings()).isEmpty();
    }

    @Test
    void reportsUnreadableDirectoriesAsWarnings() throws IOException {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions are required to make a directory unreadable");
        write("src/main/java/Ok.java", "class Ok {}");
        Path locked = dir.resolve("src/main/java/locked");
        Files.createDirectories(locked);
        Files.setPosixFilePermissions(locked, Set.of());
        try {
            Assumptions.assumeFalse(Files.isReadable(locked), "directory permissions are not enforced for this user");

            LoadedSources sources = new SourceLoader().load(dir, List.of(), SourceLimits.DEFAULT);

            assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly("src/main/java/Ok.java");
            assertThat(sources.warnings()).singleElement().satisfies(w -> {
                assertThat(w.code()).isEqualTo("FILE_UNREADABLE");
                assertThat(w.location()).isEqualTo("src/main/java/locked");
                assertThat(w.message()).startsWith("Cannot read: ").doesNotContain(dir.toString());
            });
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void unreadableWarningsNeverEmbedTheExceptionMessage() {
        Warning warning = SourceLoader.unreadable("src/main/java/A.java", new IOException("C:\\Users\\someone\\project\\A.java"));

        assertThat(warning).isEqualTo(new Warning("FILE_UNREADABLE", "Cannot read: IOException", "src/main/java/A.java"));
    }

    @Test
    void skipsFilesAboveTheSizeLimitWithAWarning() throws IOException {
        write("src/main/java/Big.java", "x".repeat(200));
        write("src/main/java/Small.java", "class Small {}");

        LoadedSources sources = new SourceLoader().load(dir, List.of(), new SourceLimits(10, 100));

        assertThat(sources.javaFiles()).extracting(SourceFile::relativePath).containsExactly("src/main/java/Small.java");
        assertThat(sources.warnings()).singleElement().satisfies(w -> {
            assertThat(w.code()).isEqualTo("FILE_TOO_LARGE");
            assertThat(w.location()).isEqualTo("src/main/java/Big.java");
        });
    }

    @Test
    void failsWhenThereAreTooManyFiles() throws IOException {
        write("src/main/java/A.java", "class A {}");
        write("src/main/java/B.java", "class B {}");

        assertThatThrownBy(() -> new SourceLoader().load(dir, List.of(), new SourceLimits(1, 1000)))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("Too many Java files: 2");
    }

    @Test
    void failsForAMissingDirectory() {
        assertThatThrownBy(() -> new SourceLoader().load(dir.resolve("nope"), List.of(), SourceLimits.DEFAULT))
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("Project directory not found");
    }
}
