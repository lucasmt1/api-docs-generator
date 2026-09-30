package dev.apidocs.core.analysis;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Evaluates compile-time String constants used in annotations, e.g. {@code @RequestMapping(ApiPaths.ORDERS)}. */
public final class ConstantResolver {

    private final TypeIndex index;

    public ConstantResolver(TypeIndex index) {
        this.index = index;
    }

    public Optional<String> resolveString(Expression expression, ParsedUnit context) {
        return resolve(expression, context, new HashSet<>());
    }

    private Optional<String> resolve(Expression expression, ParsedUnit context, Set<String> visiting) {
        if (expression instanceof StringLiteralExpr literal) {
            return Optional.of(literal.asString());
        }
        if (expression instanceof TextBlockLiteralExpr block) {
            return Optional.of(block.asString());
        }
        if (expression instanceof EnclosedExpr enclosed) {
            return resolve(enclosed.getInner(), context, visiting);
        }
        if (expression instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
            Optional<String> left = resolve(binary.getLeft(), context, visiting);
            Optional<String> right = resolve(binary.getRight(), context, visiting);
            return left.isPresent() && right.isPresent() ? Optional.of(left.get() + right.get()) : Optional.empty();
        }
        if (expression instanceof NameExpr name) {
            return resolveName(name.getNameAsString(), expression, context, visiting);
        }
        if (expression instanceof FieldAccessExpr access) {
            return index.resolve(access.getScope().toString(), context)
                    .flatMap(type -> fieldValue(type, access.getNameAsString(), visiting));
        }
        return Optional.empty();
    }

    private Optional<String> resolveName(String name, Expression expression, ParsedUnit context, Set<String> visiting) {
        Optional<Node> current = expression.getParentNode();
        while (current.isPresent()) {
            if (current.get() instanceof TypeDeclaration<?> type) {
                Optional<String> value = type.getFullyQualifiedName()
                        .flatMap(index::byQualifiedName)
                        .flatMap(indexed -> fieldValue(indexed, name, visiting));
                if (value.isPresent()) {
                    return value;
                }
            }
            current = current.get().getParentNode();
        }
        for (ImportDeclaration imported : context.cu().getImports()) {
            if (!imported.isStatic()) {
                continue;
            }
            Optional<String> value;
            if (imported.isAsterisk()) {
                value = index.byQualifiedName(imported.getNameAsString()).flatMap(t -> fieldValue(t, name, visiting));
            } else if (imported.getName().getIdentifier().equals(name)) {
                value = imported.getName().getQualifier()
                        .flatMap(qualifier -> index.byQualifiedName(qualifier.asString()))
                        .flatMap(t -> fieldValue(t, name, visiting));
            } else {
                value = Optional.empty();
            }
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> fieldValue(TypeIndex.IndexedType type, String fieldName, Set<String> visiting) {
        String key = type.qualifiedName() + "#" + fieldName;
        if (!visiting.add(key)) {
            return Optional.empty();
        }
        try {
            return type.declaration().getFieldByName(fieldName)
                    .flatMap(field -> field.getVariables().stream()
                            .filter(v -> v.getNameAsString().equals(fieldName))
                            .findFirst())
                    .flatMap(VariableDeclarator::getInitializer)
                    .flatMap(initializer -> resolve(initializer, type.unit(), visiting));
        } finally {
            visiting.remove(key);
        }
    }
}
