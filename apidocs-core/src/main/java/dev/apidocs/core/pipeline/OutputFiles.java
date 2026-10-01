package dev.apidocs.core.pipeline;

import java.util.Set;

/**
 * Names of everything apidocs writes into an output folder. The pipeline writes them and {@link OutputWriter} only
 * replaces a folder made of nothing else, so both must agree on this one list.
 */
final class OutputFiles {

    static final String README = "README.md";
    static final String TECHNICAL_DOCUMENTATION = "technical-documentation.md";
    static final String API_REFERENCE = "api-reference.md";
    static final String ARCHITECTURE_OVERVIEW = "architecture-overview.md";
    static final String OPENAPI = "openapi.yaml";
    static final String MODEL = "model.json";
    static final String REPORT = "generation-report.json";
    /** Directory with one markdown file per recorded prompt (dry-run only). */
    static final String PROMPTS_DIR = "prompts";

    /** Regular files that may sit directly in an output folder. */
    static final Set<String> FILES = Set.of(README, TECHNICAL_DOCUMENTATION, API_REFERENCE, ARCHITECTURE_OVERVIEW,
            OPENAPI, MODEL, REPORT);

    /**
     * Files a file manager adds to any folder it shows. They do not make an output folder foreign and are deleted
     * together with it when it is replaced.
     */
    static final Set<String> OS_METADATA = Set.of(".DS_Store", "Thumbs.db", "desktop.ini");

    private OutputFiles() {
    }
}
