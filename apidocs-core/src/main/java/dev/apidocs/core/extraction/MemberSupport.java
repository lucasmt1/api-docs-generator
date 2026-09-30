package dev.apidocs.core.extraction;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.apidocs.core.model.MethodRef;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

final class MemberSupport {

    private MemberSupport() {
    }

    /** Instance field name to simple type name, in declaration order. */
    static Map<String, String> fieldTypes(ClassOrInterfaceDeclaration declaration) {
        Map<String, String> types = new LinkedHashMap<>();
        for (FieldDeclaration field : declaration.getFields()) {
            if (field.isStatic()) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                types.put(variable.getNameAsString(), simpleTypeName(variable.getType()));
            }
        }
        return types;
    }

    static String simpleTypeName(Type type) {
        return type instanceof ClassOrInterfaceType classType ? classType.getNameAsString() : type.asString();
    }

    /** The field a call is made on: {@code repository.save()} or {@code this.repository.save()}. */
    static Optional<String> fieldName(Expression scope) {
        if (scope instanceof NameExpr name) {
            return Optional.of(name.getNameAsString());
        }
        if (scope instanceof FieldAccessExpr access && access.getScope() instanceof ThisExpr) {
            return Optional.of(access.getNameAsString());
        }
        return Optional.empty();
    }

    /**
     * Calls the method body makes on a field whose type satisfies {@code isCollaborator}, in first-seen order
     * without duplicates.
     */
    static List<MethodRef> fieldCalls(MethodDeclaration method, Map<String, String> fieldTypes,
            Predicate<String> isCollaborator) {
        Set<MethodRef> calls = new LinkedHashSet<>();
        method.getBody().ifPresent(body -> {
            for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
                call.getScope()
                        .flatMap(MemberSupport::fieldName)
                        .map(fieldTypes::get)
                        .filter(isCollaborator)
                        .ifPresent(typeName -> calls.add(new MethodRef(typeName, call.getNameAsString())));
            }
        });
        return List.copyOf(calls);
    }
}
