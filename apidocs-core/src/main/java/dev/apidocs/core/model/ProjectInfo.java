package dev.apidocs.core.model;

import java.util.List;

/** Metadata read from the build file and Spring configuration. Empty strings mean "unknown". */
public record ProjectInfo(
        String name,
        String description,
        String version,
        String javaVersion,
        String springBootVersion,
        List<String> dependencies,
        String applicationName,
        String contextPath,
        String buildTool) {

    public ProjectInfo {
        dependencies = List.copyOf(dependencies);
    }
}
