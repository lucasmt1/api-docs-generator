package dev.apidocs.core.extraction;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Exceptions a method can raise: {@code throw new X}, {@code orElseThrow} suppliers and the {@code throws} clause. */
final class ExceptionScanner {

    private ExceptionScanner() {
    }

    /** Simple names in source order (a single pre-order walk), then the {@code throws} clause. */
    static List<String> scan(MethodDeclaration method) {
        Set<String> names = new LinkedHashSet<>();
        method.getBody().ifPresent(body -> body.walk(Node.TreeTraversal.PREORDER, node -> {
            if (node instanceof ThrowStmt statement && statement.getExpression() instanceof ObjectCreationExpr creation) {
                names.add(creation.getType().getNameAsString());
            } else if (node instanceof MethodCallExpr call && call.getNameAsString().equals("orElseThrow")) {
                for (Expression argument : call.getArguments()) {
                    if (argument instanceof MethodReferenceExpr reference && reference.getIdentifier().equals("new")) {
                        names.add(simpleName(reference.getScope().toString()));
                    }
                    suppliedCreations(argument).forEach(creation -> names.add(creation.getType().getNameAsString()));
                }
            }
        }));
        method.getThrownExceptions().forEach(type -> names.add(simpleName(type.asString())));
        return List.copyOf(names);
    }

    /** The exceptions a supplier lambda returns; creations nested in their arguments are not thrown. */
    private static List<ObjectCreationExpr> suppliedCreations(Expression supplier) {
        if (!(supplier instanceof LambdaExpr lambda)) {
            return List.of();
        }
        List<Expression> returned = new ArrayList<>();
        if (lambda.getBody() instanceof ExpressionStmt expression) {
            returned.add(expression.getExpression());
        } else {
            lambda.getBody().findAll(ReturnStmt.class, statement -> belongsTo(statement, lambda))
                    .forEach(statement -> statement.getExpression().ifPresent(returned::add));
        }
        List<ObjectCreationExpr> creations = new ArrayList<>();
        returned.forEach(expression -> collectCreations(expression, creations));
        return creations;
    }

    private static void collectCreations(Expression expression, List<ObjectCreationExpr> creations) {
        if (expression instanceof ObjectCreationExpr creation) {
            creations.add(creation);
        } else if (expression instanceof ConditionalExpr conditional) {
            collectCreations(conditional.getThenExpr(), creations);
            collectCreations(conditional.getElseExpr(), creations);
        } else if (expression instanceof EnclosedExpr enclosed) {
            collectCreations(enclosed.getInner(), creations);
        }
    }

    /** True when the nearest enclosing lambda of the node is {@code lambda} (not a lambda nested inside it). */
    private static boolean belongsTo(Node node, LambdaExpr lambda) {
        return node.findAncestor(LambdaExpr.class).filter(enclosing -> enclosing == lambda).isPresent();
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
