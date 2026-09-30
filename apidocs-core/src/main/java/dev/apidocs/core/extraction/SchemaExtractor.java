package dev.apidocs.core.extraction;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.Javadocs;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Extracts the wire shape (fields, types, validation) of every schema requested during extraction. */
public final class SchemaExtractor {

    /**
     * Upper bound on extracted schemas. Analyzed code is untrusted, and a legal generic such as
     * {@code class Node<T> { Node<Node<T>> next; }} makes the registry request new schemas forever.
     */
    static final int MAX_SCHEMAS = 1000;

    private final TypeIndex index;
    private final TypeResolver resolver;
    private final SchemaRegistry registry;
    private final Set<String> entityQualifiedNames;
    private final int maxSchemas;

    public SchemaExtractor(TypeIndex index, TypeResolver resolver, SchemaRegistry registry,
            Set<String> entityQualifiedNames) {
        this(index, resolver, registry, entityQualifiedNames, MAX_SCHEMAS);
    }

    SchemaExtractor(TypeIndex index, TypeResolver resolver, SchemaRegistry registry,
            Set<String> entityQualifiedNames, int maxSchemas) {
        this.index = index;
        this.resolver = resolver;
        this.registry = registry;
        this.entityQualifiedNames = Set.copyOf(entityQualifiedNames);
        this.maxSchemas = maxSchemas;
    }

    public List<SchemaInfo> extractAll() {
        Map<String, SchemaInfo> schemas = new TreeMap<>();
        Optional<SchemaRegistry.SchemaRequest> next;
        while ((next = registry.next()).isPresent()) {
            if (schemas.size() >= maxSchemas) {
                registry.addWarning(Warning.of("SCHEMA_LIMIT_REACHED",
                        "Schema extraction stopped after " + maxSchemas + " schemas"));
                break;
            }
            SchemaRegistry.SchemaRequest request = next.get();
            schemas.put(request.schemaName(), extract(request));
        }
        registry.synthetic().forEach(schema -> schemas.putIfAbsent(schema.name(), schema));
        return List.copyOf(schemas.values());
    }

    private SchemaInfo extract(SchemaRegistry.SchemaRequest request) {
        TypeIndex.IndexedType type = request.type();
        TypeDeclaration<?> declaration = type.declaration();
        String description = Javadocs.of(declaration);
        if (declaration instanceof EnumDeclaration enumDeclaration) {
            List<String> values = enumDeclaration.getEntries().stream()
                    .map(EnumConstantDeclaration::getNameAsString)
                    .toList();
            return new SchemaInfo(request.schemaName(), type.qualifiedName(), SchemaKind.ENUM, List.of(), values,
                    description, false);
        }
        List<FieldInfo> fields = new ArrayList<>();
        collectFields(type, request.typeArguments(), fields, new HashSet<>());
        return new SchemaInfo(request.schemaName(), type.qualifiedName(), SchemaKind.OBJECT, fields, List.of(),
                description, entityQualifiedNames.contains(type.qualifiedName()));
    }

    private void collectFields(TypeIndex.IndexedType type, Map<String, TypeRef> typeVariables, List<FieldInfo> fields,
            Set<String> visited) {
        if (!visited.add(type.qualifiedName())) {
            return;
        }
        if (type.declaration() instanceof RecordDeclaration record) {
            for (Parameter component : record.getParameters()) {
                addField(fields, component.getNameAsString(), component.getType(), component, "", type.unit(),
                        typeVariables);
            }
            return;
        }
        if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration)) {
            return;
        }
        for (ClassOrInterfaceType parent : declaration.getExtendedTypes()) {
            Optional<TypeIndex.IndexedType> superType = index.resolve(parent.getNameWithScope(), type.unit());
            if (superType.isPresent()) {
                collectFields(superType.get(), bindSuperTypeArguments(superType.get(), parent, type.unit(), typeVariables),
                        fields, visited);
            }
        }
        for (FieldDeclaration field : declaration.getFields()) {
            if (field.isStatic() || field.hasModifier(Modifier.Keyword.TRANSIENT)) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                addField(fields, variable.getNameAsString(), variable.getType(), field, Javadocs.of(field), type.unit(),
                        typeVariables);
            }
        }
    }

    private Map<String, TypeRef> bindSuperTypeArguments(TypeIndex.IndexedType superType, ClassOrInterfaceType reference,
            ParsedUnit unit, Map<String, TypeRef> typeVariables) {
        if (!(superType.declaration() instanceof NodeWithTypeParameters<?> generic)) {
            return Map.of();
        }
        List<Type> arguments = reference.getTypeArguments().map(args -> List.<Type>copyOf(args)).orElse(List.of());
        Map<String, TypeRef> bound = new HashMap<>();
        for (int i = 0; i < generic.getTypeParameters().size() && i < arguments.size(); i++) {
            bound.put(generic.getTypeParameters().get(i).getNameAsString(),
                    resolver.resolve(arguments.get(i), unit, typeVariables));
        }
        return bound;
    }

    private void addField(List<FieldInfo> fields, String javaName, Type type, NodeWithAnnotations<?> annotated,
            String description, ParsedUnit unit, Map<String, TypeRef> typeVariables) {
        if (Annotations.has(annotated, "JsonIgnore")) {
            return;
        }
        String name = Annotations.find(annotated, "JsonProperty")
                .flatMap(annotation -> Annotations.firstAttribute(annotation, "value"))
                .flatMap(Annotations::stringLiteral)
                .filter(value -> !value.isBlank())
                .orElse(javaName);
        List<Constraint> constraints = Constraints.from(annotated);
        boolean required = type.isPrimitiveType() || Constraints.impliesRequired(constraints);
        fields.add(new FieldInfo(name, resolver.resolve(type, unit, typeVariables), required, constraints, description));
    }
}
