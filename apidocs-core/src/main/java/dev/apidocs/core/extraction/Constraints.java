package dev.apidocs.core.extraction;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.model.Constraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads Bean Validation constraints from fields and parameters. */
final class Constraints {

    static final Set<String> SUPPORTED = Set.of("NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max",
            "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero", "Email",
            "Pattern", "Past", "PastOrPresent", "Future", "FutureOrPresent", "Digits");
    private static final Set<String> REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");
    private static final Set<String> IGNORED_ATTRIBUTES = Set.of("message", "groups", "payload", "flags");

    private Constraints() {
    }

    static List<Constraint> from(NodeWithAnnotations<?> node) {
        List<Constraint> constraints = new ArrayList<>();
        for (AnnotationExpr annotation : node.getAnnotations()) {
            String name = Annotations.simpleName(annotation);
            if (!SUPPORTED.contains(name)) {
                continue;
            }
            Map<String, String> attributes = new TreeMap<>();
            if (annotation instanceof SingleMemberAnnotationExpr single) {
                attributes.put("value", Annotations.valueText(single.getMemberValue()));
            } else if (annotation instanceof NormalAnnotationExpr normal) {
                for (MemberValuePair pair : normal.getPairs()) {
                    if (!IGNORED_ATTRIBUTES.contains(pair.getNameAsString())) {
                        attributes.put(pair.getNameAsString(), Annotations.valueText(pair.getValue()));
                    }
                }
            }
            constraints.add(new Constraint(name, attributes));
        }
        return constraints;
    }

    static boolean impliesRequired(List<Constraint> constraints) {
        return constraints.stream().anyMatch(c -> REQUIRED.contains(c.name()));
    }
}
