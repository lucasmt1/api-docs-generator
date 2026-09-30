package dev.apidocs.core.analysis;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.List;
import java.util.Optional;

/** Helpers to read annotations regardless of whether they are written with simple or qualified names. */
public final class Annotations {

    private Annotations() {
    }

    public static String simpleName(AnnotationExpr annotation) {
        return annotation.getName().getIdentifier();
    }

    public static Optional<AnnotationExpr> find(NodeWithAnnotations<?> node, String... simpleNames) {
        List<String> names = List.of(simpleNames);
        return node.getAnnotations().stream().filter(a -> names.contains(simpleName(a))).findFirst();
    }

    public static boolean has(NodeWithAnnotations<?> node, String... simpleNames) {
        return find(node, simpleNames).isPresent();
    }

    /** Attribute value; {@code "value"} also matches the member of a single-member annotation. */
    public static Optional<Expression> attribute(AnnotationExpr annotation, String name) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return "value".equals(name) ? Optional.of(single.getMemberValue()) : Optional.empty();
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(name))
                    .map(MemberValuePair::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }

    public static Optional<Expression> firstAttribute(AnnotationExpr annotation, String... names) {
        for (String name : names) {
            Optional<Expression> value = attribute(annotation, name);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /** Elements of an array initializer, or the expression itself. */
    public static List<Expression> elements(Expression expression) {
        return expression instanceof ArrayInitializerExpr array ? List.copyOf(array.getValues()) : List.of(expression);
    }

    public static Optional<String> stringLiteral(Expression expression) {
        return expression instanceof StringLiteralExpr literal ? Optional.of(literal.asString()) : Optional.empty();
    }

    public static Optional<Boolean> booleanLiteral(Expression expression) {
        return expression instanceof BooleanLiteralExpr literal ? Optional.of(literal.getValue()) : Optional.empty();
    }

    /** {@code HttpStatus.CREATED} gives CREATED, {@code Foo.class} gives Foo, a plain name gives itself. */
    public static String lastIdentifier(Expression expression) {
        if (expression instanceof FieldAccessExpr access) {
            return access.getNameAsString();
        }
        if (expression instanceof NameExpr name) {
            return name.getNameAsString();
        }
        if (expression instanceof ClassExpr classExpr) {
            return classExpr.getType() instanceof ClassOrInterfaceType type
                    ? type.getNameAsString()
                    : classExpr.getType().asString();
        }
        return expression.toString();
    }

    /** Literal value as plain text: strings unquoted and unescaped, numbers without suffix, negatives kept. */
    public static String valueText(Expression expression) {
        if (expression instanceof StringLiteralExpr literal) {
            return literal.asString();
        }
        if (expression instanceof LongLiteralExpr literal) {
            return literal.getValue().replaceAll("[lL]$", "");
        }
        if (expression instanceof LiteralStringValueExpr literal) {
            return literal.getValue();
        }
        if (expression instanceof BooleanLiteralExpr literal) {
            return String.valueOf(literal.getValue());
        }
        if (expression instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.MINUS) {
            return "-" + valueText(unary.getExpression());
        }
        return expression.toString();
    }
}
