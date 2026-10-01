package dev.apidocs.core.pipeline;

import dev.apidocs.core.ApiDocsException;
import dev.apidocs.core.ConfigException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Writes the output folder all at once: nothing half-written is ever left in place. */
public final class OutputWriter {

    private static final System.Logger LOG = System.getLogger(OutputWriter.class.getName());

    /** Deletes a file or folder tree; a seam so tests can simulate a locked leftover. */
    @FunctionalInterface
    interface Remover {

        void remove(Path path) throws IOException;
    }

    private final Remover remover;

    public OutputWriter() {
        this(OutputWriter::deleteRecursively);
    }

    OutputWriter(Remover remover) {
        this.remover = remover;
    }

    /**
     * Read-only early check that {@link #writeAtomically} would accept the folder, so a refused {@code --output}
     * fails before any paid LLM call; it creates nothing. The write repeats the check (the folder may change).
     */
    public void verifyTarget(Path outputDir) {
        Path target = target(outputDir);
        try {
            checkTarget(target);
        } catch (IOException e) {
            throw new ApiDocsException("Could not inspect the output directory " + target + ": " + e.getMessage(), e);
        }
    }

    public void writeAtomically(Path outputDir, Map<String, String> files) {
        Path target = target(outputDir);
        Path parent = target.getParent();
        String name = target.getFileName().toString();
        try {
            Files.createDirectories(parent);
            checkTarget(target);
            cleanStale(parent, name);
            Path temp = parent.resolve("." + name + ".apidocs-tmp-" + UUID.randomUUID());
            try {
                for (Map.Entry<String, String> file : files.entrySet()) {
                    Path path = temp.resolve(file.getKey()).normalize();
                    if (!path.startsWith(temp)) {
                        throw new IllegalArgumentException("Invalid output file name: " + file.getKey());
                    }
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, file.getValue().replace("\r\n", "\n"), StandardCharsets.UTF_8);
                }
                replace(temp, target, parent, name);
            } finally {
                discard(temp);
            }
        } catch (IOException e) {
            throw new ApiDocsException("Could not write the documentation to " + target + ": " + e.getMessage(), e);
        }
    }

    private static Path target(Path outputDir) {
        Path target = outputDir.toAbsolutePath().normalize();
        if (target.getParent() == null) {
            throw new ConfigException("Invalid output directory: " + target);
        }
        return target;
    }

    /**
     * A non-empty folder is replaced only if it is a previous apidocs output: it has a generation report and holds
     * nothing but the files apidocs writes. Anything else may be the user's own data, which is never deleted.
     */
    private static void checkTarget(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        if (!Files.isDirectory(target)) {
            throw new ConfigException("Output path exists and is not a directory: " + target);
        }
        if (entries(target).isEmpty()) {
            return;
        }
        Optional<String> foreign = firstForeignEntry(target);
        if (foreign.isPresent()) {
            throw refusal(target, "contains '" + foreign.get() + "', which apidocs did not write");
        }
        if (!Files.exists(target.resolve(OutputFiles.REPORT))) {
            throw refusal(target, "has no " + OutputFiles.REPORT + ", so it is not a previous apidocs output");
        }
    }

    private static ConfigException refusal(Path target, String reason) {
        return new ConfigException("Output directory " + target + " is not empty and " + reason
                + "; choose another --output.");
    }

    /** The first entry (in name order) that apidocs would not have written, relative to the folder. */
    private static Optional<String> firstForeignEntry(Path target) throws IOException {
        for (Path entry : entries(target)) {
            String name = entry.getFileName().toString();
            if (name.equals(OutputFiles.PROMPTS_DIR) && isPlainDirectory(entry)) {
                for (Path prompt : entries(entry)) {
                    String promptName = prompt.getFileName().toString();
                    boolean markdown = promptName.endsWith(".md") && isRegularFile(prompt);
                    if (!markdown && !isOsMetadata(prompt)) {
                        return Optional.of(OutputFiles.PROMPTS_DIR + "/" + promptName);
                    }
                }
            } else if (!(OutputFiles.FILES.contains(name) && isRegularFile(entry)) && !isOsMetadata(entry)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static boolean isOsMetadata(Path entry) {
        return OutputFiles.OS_METADATA.contains(entry.getFileName().toString()) && isRegularFile(entry);
    }

    private static boolean isRegularFile(Path entry) {
        return Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS);
    }

    /** A real directory: not a symlink and not an NTFS junction (a directory that is also "other"). */
    private static boolean isPlainDirectory(Path entry) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isDirectory() && !attributes.isOther();
    }

    private static List<Path> entries(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().toList();
        }
    }

    private void replace(Path temp, Path target, Path parent, String name) throws IOException {
        Path backup = null;
        if (Files.exists(target)) {
            backup = parent.resolve("." + name + ".apidocs-old-" + UUID.randomUUID());
            Files.move(target, backup);
        }
        try {
            move(temp, target);
        } catch (IOException e) {
            if (backup != null && !Files.exists(target)) {
                try {
                    Files.move(backup, target);
                } catch (IOException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            throw e;
        }
        if (backup != null) {
            discard(backup);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    private void cleanStale(Path parent, String name) throws IOException {
        String prefix = "." + name + ".apidocs-";
        try (Stream<Path> siblings = Files.list(parent)) {
            for (Path sibling : siblings.filter(p -> p.getFileName().toString().startsWith(prefix)).toList()) {
                discard(sibling);
            }
        }
    }

    /**
     * Best-effort housekeeping: a leftover that cannot be removed right now (an antivirus or indexer lock, say) must
     * neither fail a run whose output is already in place nor hide the error that made it fail. The next run's
     * {@link #cleanStale} tries again.
     */
    private void discard(Path path) {
        try {
            remover.remove(path);
        } catch (IOException | UncheckedIOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Could not remove {0}: {1}", path, e.getMessage());
        }
    }

    /** Deletes a tree without following links: a symlink or NTFS junction is removed, never what it points to. */
    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isOther()) {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
