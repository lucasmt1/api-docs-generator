package dev.apidocs.core.extraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.HttpStatuses;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Infers HTTP statuses from {@code @ResponseStatus} and from {@code ResponseEntity} builder calls. */
final class StatusHeuristics {

    private static final Map<String, Integer> SHORTCUTS = Map.of("ok", 200, "created", 201, "accepted", 202,
            "noContent", 204, "badRequest", 400, "notFound", 404, "unprocessableEntity", 422,
            "unprocessableContent", 422, "internalServerError", 500);

    private StatusHeuristics() {
    }

    /** Status declared by {@code @ResponseStatus(X)}, {@code value = X} or {@code code = X} on the node. */
    static OptionalInt responseStatusCode(NodeWithAnnotations<?> node) {
        Optional<AnnotationExpr> responseStatus = Annotations.find(node, "ResponseStatus");
        if (responseStatus.isEmpty()) {
            return OptionalInt.empty();
        }
        return Annotations.firstAttribute(responseStatus.get(), "value", "code")
                .map(HttpStatuses::code)
                .orElse(OptionalInt.empty());
    }

    static List<Integer> responseEntityStatuses(Node body) {
        List<Integer> statuses = new ArrayList<>();
        for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
            if (!calledOnResponseEntity(call)) {
                continue;
            }
            String name = call.getNameAsString();
            if (name.equals("status") && call.getArguments().size() == 1) {
                HttpStatuses.code(call.getArgument(0)).ifPresent(statuses::add);
            } else if (SHORTCUTS.containsKey(name)) {
                statuses.add(SHORTCUTS.get(name));
            }
        }
        return statuses;
    }

    private static boolean calledOnResponseEntity(MethodCallExpr call) {
        return call.getScope()
                .map(scope -> scope instanceof NameExpr name && name.getNameAsString().equals("ResponseEntity")
                        || scope instanceof FieldAccessExpr access && access.getNameAsString().equals("ResponseEntity"))
                .orElse(false);
    }
}
