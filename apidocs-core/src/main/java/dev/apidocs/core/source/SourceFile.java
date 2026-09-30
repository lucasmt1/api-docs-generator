package dev.apidocs.core.source;

import java.nio.file.Path;

/** A Java file of the analyzed project. {@code relativePath} always uses '/'. */
public record SourceFile(Path path, String relativePath, String content) {
}
