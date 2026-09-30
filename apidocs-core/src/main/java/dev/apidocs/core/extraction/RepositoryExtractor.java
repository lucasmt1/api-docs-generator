package dev.apidocs.core.extraction;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import dev.apidocs.core.analysis.Annotations;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.RepositoryInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds data-access components; used only for architectural relations. */
public final class RepositoryExtractor {

    private static final Set<String> REPOSITORY_BASES = Set.of("Repository", "CrudRepository", "ListCrudRepository",
            "PagingAndSortingRepository", "ListPagingAndSortingRepository", "JpaRepository", "MongoRepository",
            "ReactiveCrudRepository", "ReactiveMongoRepository", "R2dbcRepository", "ReactiveSortingRepository");

    private final TypeIndex index;

    public RepositoryExtractor(TypeIndex index) {
        this.index = index;
    }

    public List<RepositoryInfo> extract() {
        List<RepositoryInfo> repositories = new ArrayList<>();
        for (TypeIndex.IndexedType type : index.all()) {
            if (!(type.declaration() instanceof ClassOrInterfaceDeclaration declaration)) {
                continue;
            }
            Optional<ClassOrInterfaceType> base = declaration.isInterface()
                    ? declaration.getExtendedTypes().stream()
                            .filter(parent -> REPOSITORY_BASES.contains(parent.getNameAsString()))
                            .findFirst()
                    : Optional.empty();
            if (base.isEmpty() && !Annotations.has(declaration, "Repository")) {
                continue;
            }
            List<Type> arguments = base.flatMap(ClassOrInterfaceType::getTypeArguments)
                    .map(args -> List.<Type>copyOf(args))
                    .orElse(List.of());
            String entity = arguments.isEmpty() ? "" : MemberSupport.simpleTypeName(arguments.get(0));
            String idType = arguments.size() < 2 ? "" : MemberSupport.simpleTypeName(arguments.get(1));
            repositories.add(new RepositoryInfo(type.simpleName(), type.qualifiedName(), entity, idType));
        }
        return repositories;
    }
}
