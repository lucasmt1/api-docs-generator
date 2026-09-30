package dev.apidocs.core.analysis;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** All types declared in the analyzed project, resolvable like the Java compiler would (best effort). */
public final class TypeIndex {

    public record IndexedType(String qualifiedName, String simpleName, TypeDeclaration<?> declaration, ParsedUnit unit) {

        public boolean isEnum() {
            return declaration.isEnumDeclaration();
        }

        public boolean isRecord() {
            return declaration.isRecordDeclaration();
        }

        public boolean isInterface() {
            return declaration instanceof ClassOrInterfaceDeclaration c && c.isInterface();
        }

        public boolean isClass() {
            return declaration instanceof ClassOrInterfaceDeclaration c && !c.isInterface();
        }

        public String packageName() {
            return unit.packageName();
        }
    }

    private final Map<String, IndexedType> byQualifiedName = new TreeMap<>();
    private final Map<String, List<IndexedType>> bySimpleName = new HashMap<>();

    private TypeIndex() {
    }

    public static TypeIndex build(List<ParsedUnit> units) {
        TypeIndex index = new TypeIndex();
        for (ParsedUnit unit : units) {
            for (TypeDeclaration<?> type : unit.cu().findAll(TypeDeclaration.class)) {
                type.getFullyQualifiedName().ifPresent(fqn ->
                        index.add(new IndexedType(fqn, type.getNameAsString(), type, unit)));
            }
        }
        return index;
    }

    private void add(IndexedType type) {
        if (byQualifiedName.putIfAbsent(type.qualifiedName(), type) == null) {
            bySimpleName.computeIfAbsent(type.simpleName(), key -> new ArrayList<>()).add(type);
        }
    }

    public Collection<IndexedType> all() {
        return Collections.unmodifiableCollection(byQualifiedName.values());
    }

    public Optional<IndexedType> byQualifiedName(String qualifiedName) {
        return Optional.ofNullable(byQualifiedName.get(qualifiedName));
    }

    public List<IndexedType> bySimpleName(String simpleName) {
        return bySimpleName.getOrDefault(simpleName, List.of());
    }

    /**
     * Resolves a type name as written in {@code context}: exact FQN, types of the same file, single-type imports,
     * same package, wildcard imports, and finally a project-wide unique simple name. A single-type import of a
     * type outside the project wins over project types with the same simple name.
     */
    public Optional<IndexedType> resolve(String typeName, ParsedUnit context) {
        String name = stripDecorations(typeName);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        IndexedType exact = byQualifiedName.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        int dot = name.indexOf('.');
        String first = dot < 0 ? name : name.substring(0, dot);
        String rest = dot < 0 ? "" : name.substring(dot);

        for (TypeDeclaration<?> declared : context.cu().findAll(TypeDeclaration.class)) {
            if (declared.getNameAsString().equals(first)) {
                Optional<IndexedType> found = declared.getFullyQualifiedName().map(fqn -> byQualifiedName.get(fqn + rest));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        for (ImportDeclaration imported : context.cu().getImports()) {
            if (!imported.isAsterisk() && !imported.isStatic() && imported.getName().getIdentifier().equals(first)) {
                return Optional.ofNullable(byQualifiedName.get(imported.getNameAsString() + rest));
            }
        }
        String pkg = context.packageName();
        IndexedType samePackage = byQualifiedName.get(pkg.isEmpty() ? name : pkg + "." + name);
        if (samePackage != null) {
            return Optional.of(samePackage);
        }
        for (ImportDeclaration imported : context.cu().getImports()) {
            if (imported.isAsterisk() && !imported.isStatic()) {
                IndexedType found = byQualifiedName.get(imported.getNameAsString() + "." + name);
                if (found != null) {
                    return Optional.of(found);
                }
            }
        }
        if (dot < 0) {
            List<IndexedType> candidates = bySimpleName.getOrDefault(name, List.of());
            if (candidates.size() == 1) {
                return Optional.of(candidates.get(0));
            }
        }
        return Optional.empty();
    }

    private static String stripDecorations(String typeName) {
        String name = typeName.strip();
        if (name.contains("<")) {
            return "";
        }
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2).strip();
        }
        return name;
    }
}
