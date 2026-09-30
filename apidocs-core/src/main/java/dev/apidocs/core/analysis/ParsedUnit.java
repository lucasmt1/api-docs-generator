package dev.apidocs.core.analysis;

import com.github.javaparser.ast.CompilationUnit;
import dev.apidocs.core.source.SourceFile;

public record ParsedUnit(SourceFile file, CompilationUnit cu) {

    public String packageName() {
        return cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
    }
}
