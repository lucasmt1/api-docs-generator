package dev.apidocs.core.extraction;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.EntityField;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.RelationKind;
import dev.apidocs.core.model.Relationship;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Code Extractor for the Entities/Models layer (JPA, Spring Data JDBC and MongoDB). */
public final class EntityExtractor {

    private static final Map<String, RelationKind> RELATIONS = Map.of("OneToOne", RelationKind.ONE_TO_ONE,
            "OneToMany", RelationKind.ONE_TO_MANY, "ManyToOne", RelationKind.MANY_TO_ONE,
            "ManyToMany", RelationKind.MANY_TO_MANY);

    private final TypeIndex index;
    private final TypeResolver resolver;

    public EntityExtractor(TypeIndex index, TypeResolver resolver) {
        this.index = index;
        this.resolver = resolver;
    }

    public List<EntityInfo> extract() {
        List<EntityInfo> entities = new ArrayList<>();
        for (TypeIndex.IndexedType type : index.all()) {
            if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration) || declaration.isInterface()
                    || !Annotations.has(declaration, "Entity", "Document", "Table")) {
                continue;
            }
            List<EntityField> fields = new ArrayList<>();
            List<Relationship> relationships = new ArrayList<>();
            collect(type, declaration, "", false, fields, relationships, new HashSet<>());
            entities.add(new EntityInfo(type.simpleName(), type.qualifiedName(), tableName(declaration, type.simpleName()),
                    fields, relationships));
        }
        return entities;
    }

    private static String tableName(ClassOrInterfaceDeclaration declaration, String fallback) {
        return Annotations.find(declaration, "Table")
                .flatMap(annotation -> Annotations.firstAttribute(annotation, "name", "value"))
                .flatMap(Annotations::stringLiteral)
                .or(() -> Annotations.find(declaration, "Document")
                        .flatMap(annotation -> Annotations.firstAttribute(annotation, "collection", "value"))
                        .flatMap(Annotations::stringLiteral))
                .filter(name -> !name.isBlank())
                .orElse(fallback);
    }

    /** {@code idColumns} is true while flattening an {@code @EmbeddedId}: every column below it is part of the key. */
    private void collect(TypeIndex.IndexedType type, ClassOrInterfaceDeclaration declaration, String prefix,
            boolean idColumns, List<EntityField> fields, List<Relationship> relationships, Set<String> path) {
        if (!path.add(type.qualifiedName())) {
            return;
        }
        for (ClassOrInterfaceType parent : declaration.getExtendedTypes()) {
            index.resolve(parent.getNameWithScope(), type.unit()).ifPresent(superType -> {
                if (superType.declaration() instanceof ClassOrInterfaceDeclaration superDeclaration
                        && Annotations.has(superDeclaration, "MappedSuperclass", "Entity")) {
                    collect(superType, superDeclaration, prefix, idColumns, fields, relationships, path);
                }
            });
        }
        for (FieldDeclaration field : declaration.getFields()) {
            if (field.isStatic() || field.hasModifier(Modifier.Keyword.TRANSIENT) || Annotations.has(field, "Transient")) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                String name = prefix + variable.getNameAsString();
                Optional<AnnotationExpr> relation = field.getAnnotations().stream()
                        .filter(annotation -> RELATIONS.containsKey(Annotations.simpleName(annotation)))
                        .findFirst();
                if (relation.isPresent()) {
                    relationships.add(new Relationship(name, RELATIONS.get(Annotations.simpleName(relation.get())),
                            targetType(variable.getType()),
                            Annotations.attribute(relation.get(), "mappedBy").flatMap(Annotations::stringLiteral).orElse("")));
                    continue;
                }
                Optional<TypeIndex.IndexedType> embeddable = embeddable(variable.getType(), type.unit(), field);
                if (embeddable.isPresent()) {
                    collect(embeddable.get(), (ClassOrInterfaceDeclaration) embeddable.get().declaration(),
                            name + ".", idColumns || Annotations.has(field, "EmbeddedId"), fields, relationships,
                            new HashSet<>(path));
                    continue;
                }
                fields.add(column(name, variable, field, idColumns, type.unit()));
            }
        }
    }

    private Optional<TypeIndex.IndexedType> embeddable(Type type, ParsedUnit unit, FieldDeclaration field) {
        if (!(type instanceof ClassOrInterfaceType classType)) {
            return Optional.empty();
        }
        return index.resolve(classType.getNameWithScope(), unit)
                .filter(resolved -> resolved.declaration() instanceof ClassOrInterfaceDeclaration)
                .filter(resolved -> Annotations.has(field, "Embedded", "EmbeddedId")
                        || Annotations.has((ClassOrInterfaceDeclaration) resolved.declaration(), "Embeddable"));
    }

    private EntityField column(String name, VariableDeclarator variable, FieldDeclaration field, boolean idColumn,
            ParsedUnit unit) {
        Optional<AnnotationExpr> column = Annotations.find(field, "Column");
        String columnName = column.flatMap(annotation -> Annotations.attribute(annotation, "name"))
                .flatMap(Annotations::stringLiteral)
                .filter(value -> !value.isBlank())
                .orElse(variable.getNameAsString());
        boolean id = idColumn || Annotations.has(field, "Id", "EmbeddedId");
        boolean columnNullable = column.flatMap(annotation -> Annotations.attribute(annotation, "nullable"))
                .flatMap(Annotations::booleanLiteral).orElse(true);
        boolean nullable = columnNullable && !id && !variable.getType().isPrimitiveType()
                && !Annotations.has(field, "NotNull");
        boolean unique = column.flatMap(annotation -> Annotations.attribute(annotation, "unique"))
                .flatMap(Annotations::booleanLiteral).orElse(false);
        int length = column.flatMap(annotation -> Annotations.attribute(annotation, "length"))
                .map(Annotations::valueText)
                .map(EntityExtractor::parseIntOrZero)
                .orElse(0);
        return new EntityField(name, resolver.resolveWithoutSchemas(variable.getType(), unit), columnName, id,
                nullable, unique, length);
    }

    private static String targetType(Type type) {
        if (type instanceof ClassOrInterfaceType classType) {
            List<Type> arguments = classType.getTypeArguments().map(args -> List.<Type>copyOf(args)).orElse(List.of());
            return arguments.isEmpty()
                    ? classType.getNameAsString()
                    : MemberSupport.simpleTypeName(arguments.get(arguments.size() - 1));
        }
        return type.asString();
    }

    private static int parseIntOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
