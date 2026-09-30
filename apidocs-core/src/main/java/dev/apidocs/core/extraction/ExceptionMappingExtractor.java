package dev.apidocs.core.extraction;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.HttpStatuses;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.Warning;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/** Reads how exceptions become HTTP statuses: controller advices and {@code @ResponseStatus} exceptions. */
public final class ExceptionMappingExtractor {

    private final TypeIndex index;

    public ExceptionMappingExtractor(TypeIndex index) {
        this.index = index;
    }

    public List<ExceptionMapping> extract(List<Warning> warnings) {
        Map<String, Integer> statuses = new TreeMap<>();
        for (TypeIndex.IndexedType type : index.all()) {
            if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration)
                    || !Annotations.has(declaration, "RestControllerAdvice", "ControllerAdvice")) {
                continue;
            }
            for (MethodDeclaration method : declaration.getMethods()) {
                Optional<AnnotationExpr> handler = Annotations.find(method, "ExceptionHandler");
                if (handler.isEmpty()) {
                    continue;
                }
                OptionalInt status = handlerStatus(method);
                if (status.isEmpty()) {
                    warnings.add(new Warning("HANDLER_STATUS_UNKNOWN",
                            "Could not infer the HTTP status returned by " + method.getNameAsString(),
                            type.qualifiedName() + "#" + method.getNameAsString()));
                    continue;
                }
                handledExceptions(handler.get(), method).forEach(ex -> statuses.putIfAbsent(ex, status.getAsInt()));
            }
        }
        for (TypeIndex.IndexedType type : index.all()) {
            if (type.declaration() instanceof ClassOrInterfaceDeclaration declaration && !declaration.isInterface()) {
                StatusHeuristics.responseStatusCode(declaration)
                        .ifPresent(code -> statuses.putIfAbsent(type.simpleName(), code));
            }
        }
        return statuses.entrySet().stream().map(e -> new ExceptionMapping(e.getKey(), e.getValue())).toList();
    }

    private static List<String> handledExceptions(AnnotationExpr handler, MethodDeclaration method) {
        List<String> declared = Annotations.firstAttribute(handler, "value", "exception")
                .map(value -> Annotations.elements(value).stream().map(Annotations::lastIdentifier).toList())
                .orElse(List.of());
        if (!declared.isEmpty()) {
            return declared;
        }
        return method.getParameters().stream()
                .map(parameter -> MemberSupport.simpleTypeName(parameter.getType()))
                .filter(name -> name.endsWith("Exception") || name.endsWith("Error") || name.equals("Throwable"))
                .toList();
    }

    private static OptionalInt handlerStatus(MethodDeclaration method) {
        OptionalInt declared = StatusHeuristics.responseStatusCode(method);
        if (declared.isPresent()) {
            return declared;
        }
        if (method.getBody().isEmpty()) {
            return OptionalInt.empty();
        }
        List<Integer> fromResponseEntity = StatusHeuristics.responseEntityStatuses(method.getBody().get());
        if (!fromResponseEntity.isEmpty()) {
            return OptionalInt.of(fromResponseEntity.get(0));
        }
        for (MethodCallExpr call : method.getBody().get().findAll(MethodCallExpr.class)) {
            boolean problemDetail = call.getScope()
                    .map(scope -> scope instanceof NameExpr name && name.getNameAsString().equals("ProblemDetail"))
                    .orElse(false);
            if (problemDetail && call.getNameAsString().startsWith("forStatus") && !call.getArguments().isEmpty()) {
                OptionalInt code = HttpStatuses.code(call.getArgument(0));
                if (code.isPresent()) {
                    return code;
                }
            }
        }
        return OptionalInt.empty();
    }
}
