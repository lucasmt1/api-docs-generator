package dev.apidocs.core.extraction;

import static java.util.Map.entry;

import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import dev.apidocs.core.analysis.ParsedUnit;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Maps JavaParser types to documented {@link TypeRef}s, registering project types as schemas. */
public final class TypeResolver {

    public static final String PAGE_METADATA = "PageMetadata";

    private static final Map<String, ScalarKind> SCALARS = Map.ofEntries(
            entry("String", ScalarKind.STRING), entry("CharSequence", ScalarKind.STRING),
            entry("Character", ScalarKind.STRING), entry("URI", ScalarKind.STRING), entry("URL", ScalarKind.STRING),
            entry("Duration", ScalarKind.STRING), entry("Locale", ScalarKind.STRING), entry("Currency", ScalarKind.STRING),
            entry("Integer", ScalarKind.INTEGER), entry("Short", ScalarKind.INTEGER), entry("Byte", ScalarKind.INTEGER),
            entry("Long", ScalarKind.LONG), entry("BigInteger", ScalarKind.LONG),
            entry("Float", ScalarKind.FLOAT), entry("Double", ScalarKind.DOUBLE),
            entry("BigDecimal", ScalarKind.DECIMAL), entry("Number", ScalarKind.NUMBER),
            entry("Boolean", ScalarKind.BOOLEAN),
            entry("LocalDate", ScalarKind.DATE),
            entry("LocalDateTime", ScalarKind.DATE_TIME), entry("OffsetDateTime", ScalarKind.DATE_TIME),
            entry("ZonedDateTime", ScalarKind.DATE_TIME), entry("Instant", ScalarKind.DATE_TIME),
            entry("Date", ScalarKind.DATE_TIME), entry("Timestamp", ScalarKind.DATE_TIME),
            entry("LocalTime", ScalarKind.TIME), entry("OffsetTime", ScalarKind.TIME),
            entry("UUID", ScalarKind.UUID),
            entry("MultipartFile", ScalarKind.BINARY), entry("Resource", ScalarKind.BINARY),
            entry("InputStream", ScalarKind.BINARY), entry("InputStreamResource", ScalarKind.BINARY),
            entry("ByteArrayResource", ScalarKind.BINARY));
    private static final Set<String> COLLECTIONS = Set.of("List", "ArrayList", "LinkedList", "Set", "HashSet",
            "LinkedHashSet", "TreeSet", "SortedSet", "Collection", "Iterable", "Queue", "Deque", "Stream", "Flux");
    private static final Set<String> MAPS = Set.of("Map", "HashMap", "LinkedHashMap", "TreeMap", "SortedMap",
            "ConcurrentMap", "ConcurrentHashMap");
    private static final Set<String> WRAPPERS = Set.of("Optional", "ResponseEntity", "HttpEntity", "CompletableFuture",
            "CompletionStage", "Future", "Mono", "DeferredResult", "Callable", "WebAsyncTask", "AtomicReference");
    private static final Set<String> PAGES = Set.of("Page", "Slice", "PagedModel", "PageImpl");

    private final TypeIndex index;
    private final SchemaRegistry registry;

    public TypeResolver(TypeIndex index, SchemaRegistry registry) {
        this.index = index;
        this.registry = registry;
    }

    public TypeRef resolve(Type type, ParsedUnit context, Map<String, TypeRef> typeVariables) {
        return resolve(type, context, typeVariables, true);
    }

    /** Resolution for entity columns: project types become references without being documented as schemas. */
    public TypeRef resolveWithoutSchemas(Type type, ParsedUnit context) {
        return resolve(type, context, Map.of(), false);
    }

    private TypeRef resolve(Type type, ParsedUnit context, Map<String, TypeRef> variables, boolean register) {
        if (type instanceof PrimitiveType primitive) {
            return new ScalarType(switch (primitive.getType()) {
                case BOOLEAN -> ScalarKind.BOOLEAN;
                case CHAR -> ScalarKind.STRING;
                case BYTE, SHORT, INT -> ScalarKind.INTEGER;
                case LONG -> ScalarKind.LONG;
                case FLOAT -> ScalarKind.FLOAT;
                case DOUBLE -> ScalarKind.DOUBLE;
            });
        }
        if (type instanceof ArrayType array) {
            Type component = array.getComponentType();
            if (component instanceof PrimitiveType primitive && primitive.getType() == PrimitiveType.Primitive.BYTE) {
                return new ScalarType(ScalarKind.BINARY);
            }
            return new ArrayOf(resolve(component, context, variables, register));
        }
        if (type instanceof WildcardType wildcard) {
            return wildcard.getExtendedType()
                    .map(bound -> resolve(bound, context, variables, register))
                    .orElse(new OpaqueType("Object"));
        }
        if (type instanceof ClassOrInterfaceType classType) {
            return resolveClass(classType, context, variables, register);
        }
        return new OpaqueType(type.asString());
    }

    private TypeRef resolveClass(ClassOrInterfaceType type, ParsedUnit context, Map<String, TypeRef> variables,
            boolean register) {
        String simple = type.getNameAsString();
        List<Type> arguments = type.getTypeArguments().map(args -> List.<Type>copyOf(args)).orElse(List.of());
        if (type.getScope().isEmpty() && variables.containsKey(simple)) {
            return variables.get(simple);
        }
        Optional<TypeIndex.IndexedType> projectType = index.resolve(type.getNameWithScope(), context);
        if (projectType.isPresent()) {
            TypeIndex.IndexedType indexed = projectType.get();
            if (indexed.isEnum()) {
                return new EnumRef(register ? registry.request(indexed, List.of()) : indexed.simpleName());
            }
            List<TypeRef> resolvedArguments = arguments.stream()
                    .map(argument -> resolve(argument, context, variables, register))
                    .toList();
            return new ObjectRef(register ? registry.request(indexed, resolvedArguments) : indexed.simpleName());
        }
        ScalarKind scalar = SCALARS.get(simple);
        if (scalar != null) {
            return new ScalarType(scalar);
        }
        if (WRAPPERS.contains(simple)) {
            return arguments.isEmpty() ? new OpaqueType(simple) : resolve(arguments.get(0), context, variables, register);
        }
        if (COLLECTIONS.contains(simple)) {
            return new ArrayOf(arguments.isEmpty()
                    ? new OpaqueType("Object")
                    : resolve(arguments.get(0), context, variables, register));
        }
        if (MAPS.contains(simple)) {
            return new MapOf(arguments.size() == 2
                    ? resolve(arguments.get(1), context, variables, register)
                    : new OpaqueType("Object"));
        }
        if (PAGES.contains(simple) && arguments.size() == 1) {
            return paged(resolve(arguments.get(0), context, variables, register), register);
        }
        return new OpaqueType(simple);
    }

    private TypeRef paged(TypeRef content, boolean register) {
        String name = "PagedModel_" + SchemaRegistry.nameOf(content);
        if (register) {
            ScalarType number = new ScalarType(ScalarKind.LONG);
            registry.registerSynthetic(new SchemaInfo(PAGE_METADATA,
                    "org.springframework.data.web.PagedModel.PageMetadata", SchemaKind.OBJECT,
                    List.of(new FieldInfo("size", number, true, List.of(), ""),
                            new FieldInfo("number", number, true, List.of(), ""),
                            new FieldInfo("totalElements", number, true, List.of(), ""),
                            new FieldInfo("totalPages", number, true, List.of(), "")),
                    List.of(), "", false));
            registry.registerSynthetic(new SchemaInfo(name, "org.springframework.data.web.PagedModel", SchemaKind.OBJECT,
                    List.of(new FieldInfo("content", new ArrayOf(content), true, List.of(), ""),
                            new FieldInfo("page", new ObjectRef(PAGE_METADATA), true, List.of(), "")),
                    List.of(), "", false));
        }
        return new ObjectRef(name);
    }
}
