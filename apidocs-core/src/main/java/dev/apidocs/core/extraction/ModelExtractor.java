package dev.apidocs.core.extraction;

import dev.apidocs.core.analysis.JavaSourceParser;
import dev.apidocs.core.analysis.ParseOutcome;
import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ProjectInfo;
import dev.apidocs.core.source.LoadedSources;
import dev.apidocs.core.source.ProjectInfoReader;
import dev.apidocs.core.source.SourceLimits;
import dev.apidocs.core.source.SourceLoader;
import java.nio.file.Path;
import java.util.List;

/** Convenience facade: load, parse and extract a project in one call. */
public final class ModelExtractor {

    public ApiModel extract(Path projectDir, List<String> excludes, SourceLimits limits) {
        LoadedSources sources = new SourceLoader().load(projectDir, excludes, limits);
        ProjectInfo project = new ProjectInfoReader().read(projectDir);
        ParseOutcome parsed = new JavaSourceParser().parse(sources.javaFiles());
        return new ModelAssembler().assemble(project, parsed, sources.warnings());
    }
}
