package dev.apidocs.core.source;

import dev.apidocs.core.AnalysisException;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.model.Warnings;
import dev.apidocs.core.support.Globs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Collects {@code src/main/java} sources of a project (any module), never following symlinks. */
public final class SourceLoader {

    /** Tooling and dependency directories, skipped wherever they appear. */
    private static final Set<String> IGNORED_DIRECTORIES =
            Set.of(".git", ".idea", ".gradle", ".mvn", "node_modules");

    /** Build-output directories, skipped unless nested in a {@code src} tree (where they are just packages). */
    private static final Set<String> BUILD_OUTPUT_DIRECTORIES = Set.of("target", "build", "out");

    public LoadedSources load(Path projectDir, List<String> excludeGlobs, SourceLimits limits) {
        Path requested = projectDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(requested)) {
            throw new AnalysisException("Project directory not found: " + requested);
        }
        // The root itself may be a symlink (chosen by the user); everything below it is never followed.
        Path root = realPath(requested);
        List<Pattern> excludes = excludeGlobs.stream().map(Globs::toPattern).toList();
        List<Path> candidates = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    // NTFS junctions are reported as directories that are also "other": like symlinks, never entered
                    if (attrs.isOther() || isIgnoredDirectory(root, dir) || isTestSourceRoot(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        // Symbolic links (and other special files) are never read: the target could be any local file.
                        return FileVisitResult.CONTINUE;
                    }
                    String relative = relativize(root, file);
                    if (relative.endsWith(".java") && isMainJavaSource(relative)
                            && excludes.stream().noneMatch(p -> p.matcher(relative).matches())) {
                        candidates.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    warnings.add(unreadable(relativize(root, file), exc));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new AnalysisException("Cannot read project directory " + root + ": " + e.getMessage(), e);
        }
        if (candidates.size() > limits.maxFiles()) {
            throw new AnalysisException("Too many Java files: " + candidates.size() + " (limit " + limits.maxFiles() + ")");
        }
        candidates.sort(Comparator.comparing(path -> relativize(root, path)));

        List<SourceFile> files = new ArrayList<>();
        for (Path file : candidates) {
            String relative = relativize(root, file);
            try {
                if (Files.size(file) > limits.maxFileBytes()) {
                    warnings.add(new Warning("FILE_TOO_LARGE",
                            "Skipped file larger than " + limits.maxFileBytes() + " bytes", relative));
                    continue;
                }
                files.add(new SourceFile(file, relative, new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                warnings.add(unreadable(relative, e));
            }
        }
        return new LoadedSources(root, files, Warnings.sortedDistinct(warnings));
    }

    static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Warnings end up in the model, so the message must not carry machine-specific text such as absolute paths. */
    static Warning unreadable(String relativePath, IOException cause) {
        return new Warning("FILE_UNREADABLE", "Cannot read: " + cause.getClass().getSimpleName(), relativePath);
    }

    private static Path realPath(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            throw new AnalysisException("Cannot read project directory " + directory + ": " + e.getMessage(), e);
        }
    }

    private static boolean isIgnoredDirectory(Path root, Path dir) {
        String name = dir.getFileName().toString();
        if (IGNORED_DIRECTORIES.contains(name)) {
            return true;
        }
        return BUILD_OUTPUT_DIRECTORIES.contains(name) && !isInsideSrc(root.relativize(dir));
    }

    /** True when any segment of the root-relative path is {@code src}. */
    private static boolean isInsideSrc(Path relativeDir) {
        for (Path segment : relativeDir) {
            if (segment.toString().equals("src")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTestSourceRoot(Path dir) {
        Path parent = dir.getParent();
        return dir.getFileName().toString().equals("test")
                && parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equals("src");
    }

    static boolean isMainJavaSource(String relativePath) {
        String[] segments = relativePath.split("/");
        for (int i = 0; i + 2 < segments.length - 1; i++) {
            if (segments[i].equals("src") && segments[i + 1].equals("main") && segments[i + 2].equals("java")) {
                return true;
            }
        }
        return false;
    }
}
