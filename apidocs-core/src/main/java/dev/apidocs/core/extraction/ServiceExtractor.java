package dev.apidocs.core.extraction;

import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.Javadocs;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.ServiceMethod;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Code Extractor for the Services layer: business logic is taken from the method bodies. */
public final class ServiceExtractor {

    private final TypeIndex index;
    private final Set<String> serviceNames;
    private final Set<String> collaborators;

    public ServiceExtractor(TypeIndex index, Set<String> serviceNames, Set<String> repositoryNames) {
        this.index = index;
        this.serviceNames = Set.copyOf(serviceNames);
        Set<String> all = new HashSet<>(serviceNames);
        all.addAll(repositoryNames);
        this.collaborators = Set.copyOf(all);
    }

    public static Set<String> serviceNames(TypeIndex index) {
        return index.all().stream()
                .filter(type -> type.declaration() instanceof ClassOrInterfaceDeclaration declaration
                        && !declaration.isInterface() && Annotations.has(declaration, "Service"))
                .map(TypeIndex.IndexedType::simpleName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public List<ServiceInfo> extract() {
        List<ServiceInfo> services = new ArrayList<>();
        for (TypeIndex.IndexedType type : index.all()) {
            if (type.declaration() instanceof ClassOrInterfaceDeclaration declaration && !declaration.isInterface()
                    && serviceNames.contains(type.simpleName()) && Annotations.has(declaration, "Service")) {
                services.add(extract(type, declaration));
            }
        }
        return services;
    }

    private ServiceInfo extract(TypeIndex.IndexedType type, ClassOrInterfaceDeclaration declaration) {
        Map<String, String> fieldTypes = MemberSupport.fieldTypes(declaration);
        Optional<AnnotationExpr> classTransactional = Annotations.find(declaration, "Transactional");
        Map<String, List<MethodDeclaration>> methodsByName = new LinkedHashMap<>();
        for (MethodDeclaration method : declaration.getMethods()) {
            methodsByName.computeIfAbsent(method.getNameAsString(), name -> new ArrayList<>()).add(method);
        }
        List<ServiceMethod> methods = new ArrayList<>();
        for (MethodDeclaration method : declaration.getMethods()) {
            if (!method.isPublic() || method.isStatic()) {
                continue;
            }
            Set<String> thrown = new LinkedHashSet<>();
            Set<MethodRef> calls = new LinkedHashSet<>();
            for (MethodDeclaration reached : reachable(method, methodsByName)) {
                thrown.addAll(ExceptionScanner.scan(reached));
                calls.addAll(MemberSupport.fieldCalls(reached, fieldTypes, collaborators::contains));
            }
            Optional<AnnotationExpr> transactional = Annotations.find(method, "Transactional").or(() -> classTransactional);
            boolean readOnly = transactional
                    .flatMap(annotation -> Annotations.attribute(annotation, "readOnly"))
                    .flatMap(Annotations::booleanLiteral)
                    .orElse(false);
            methods.add(new ServiceMethod(method.getNameAsString(), method.getDeclarationAsString(false, true, true),
                    body(method), transactional.isPresent(), readOnly, List.copyOf(thrown), List.copyOf(calls),
                    Javadocs.of(method)));
        }
        return new ServiceInfo(type.simpleName(), type.qualifiedName(), dependencies(declaration), methods);
    }

    /** The method plus same-class methods it calls (without scope or through {@code this}), transitively. */
    private static List<MethodDeclaration> reachable(MethodDeclaration start, Map<String, List<MethodDeclaration>> byName) {
        List<MethodDeclaration> result = new ArrayList<>();
        Set<MethodDeclaration> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<MethodDeclaration> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            MethodDeclaration current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            result.add(current);
            current.getBody().ifPresent(body -> {
                for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
                    boolean local = call.getScope().isEmpty() || call.getScope().get() instanceof ThisExpr;
                    if (local) {
                        queue.addAll(byName.getOrDefault(call.getNameAsString(), List.of()));
                    }
                }
            });
        }
        return result;
    }

    private static List<String> dependencies(ClassOrInterfaceDeclaration declaration) {
        Set<String> dependencies = new TreeSet<>();
        for (ConstructorDeclaration constructor : declaration.getConstructors()) {
            constructor.getParameters().forEach(p -> dependencies.add(MemberSupport.simpleTypeName(p.getType())));
        }
        boolean requiredArgs = Annotations.has(declaration, "RequiredArgsConstructor");
        boolean allArgs = Annotations.has(declaration, "AllArgsConstructor");
        for (FieldDeclaration field : declaration.getFields()) {
            if (field.isStatic()) {
                continue;
            }
            boolean injected = Annotations.has(field, "Autowired", "Inject") || allArgs || requiredArgs && field.isFinal();
            if (injected) {
                field.getVariables().forEach(v -> dependencies.add(MemberSupport.simpleTypeName(v.getType())));
            }
        }
        return List.copyOf(dependencies);
    }

    private static String body(MethodDeclaration method) {
        return method.getBody()
                .flatMap(Node::getTokenRange)
                .map(TokenRange::toString)
                .map(text -> text.replace("\r\n", "\n"))
                .orElse("");
    }
}
