package dev.apidocs.core.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.source.SourceFile;
import java.util.ArrayList;
import java.util.List;

/** Static Analysis Engine entry point: turns source files into ASTs. Never compiles or runs anything. */
public final class JavaSourceParser {

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    public ParseOutcome parse(List<SourceFile> files) {
        List<ParsedUnit> units = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (SourceFile file : files) {
            ParseResult<CompilationUnit> result = parser.parse(file.content());
            if (result.isSuccessful() && result.getResult().isPresent()) {
                units.add(new ParsedUnit(file, result.getResult().get()));
            } else {
                warnings.add(toWarning(file, result.getProblems()));
            }
        }
        return new ParseOutcome(units, warnings);
    }

    private static Warning toWarning(SourceFile file, List<Problem> problems) {
        if (problems.isEmpty()) {
            return new Warning("PARSE_ERROR", "Could not parse file", file.relativePath());
        }
        Problem first = problems.get(0);
        String location = first.getLocation()
                .flatMap(range -> range.getBegin().getRange())
                .map(range -> file.relativePath() + ":" + range.begin.line)
                .orElse(file.relativePath());
        String message = first.getMessage().lines().findFirst().orElse("Parse error");
        if (message.length() > 200) {
            message = message.substring(0, 200) + "...";
        }
        return new Warning("PARSE_ERROR", message, location);
    }
}
