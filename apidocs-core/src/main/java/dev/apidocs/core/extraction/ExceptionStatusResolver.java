package dev.apidocs.core.extraction;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ExceptionMapping;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Finds the HTTP status of an exception, using the most specific mapping along its project superclasses. */
public final class ExceptionStatusResolver {

    private final TypeIndex index;
    private final Map<String, Integer> statuses = new HashMap<>();

    public ExceptionStatusResolver(TypeIndex index, List<ExceptionMapping> mappings) {
        this.index = index;
        mappings.forEach(mapping -> statuses.putIfAbsent(mapping.exception(), mapping.status()));
    }

    public OptionalInt statusOf(String exception) {
        Set<String> seen = new HashSet<>();
        String current = exception;
        while (current != null && seen.add(current)) {
            Integer status = statuses.get(current);
            if (status != null) {
                return OptionalInt.of(status);
            }
            current = superclassOf(current);
        }
        return OptionalInt.empty();
    }

    public boolean isProjectType(String simpleName) {
        return !index.bySimpleName(simpleName).isEmpty();
    }

    private String superclassOf(String simpleName) {
        List<TypeIndex.IndexedType> candidates = index.bySimpleName(simpleName);
        if (candidates.size() != 1 || !(candidates.get(0).declaration() instanceof ClassOrInterfaceDeclaration declaration)) {
            return null;
        }
        return declaration.getExtendedTypes().stream()
                .findFirst()
                .map(ClassOrInterfaceType::getNameAsString)
                .orElse(null);
    }
}
