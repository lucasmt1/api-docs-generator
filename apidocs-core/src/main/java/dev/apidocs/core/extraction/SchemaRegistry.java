package dev.apidocs.core.extraction;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.TypeParameter;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.MapOf;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.OpaqueType;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.TypeRef;
import dev.apidocs.core.model.Warning;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Assigns stable schema names to project types and queues them for extraction. */
public final class SchemaRegistry {

    public record SchemaRequest(String schemaName, TypeIndex.IndexedType type, Map<String, TypeRef> typeArguments) {
    }

    private final Map<String, String> nameByKey = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Deque<SchemaRequest> pending = new ArrayDeque<>();
    private final Map<String, SchemaInfo> synthetic = new TreeMap<>();
    private final List<Warning> warnings = new ArrayList<>();

    public String request(TypeIndex.IndexedType type, List<TypeRef> arguments) {
        String suffix = arguments.stream().map(SchemaRegistry::nameOf).collect(Collectors.joining("_"));
        String key = type.qualifiedName() + (arguments.isEmpty() ? "" : "<" + suffix + ">");
        String existing = nameByKey.get(key);
        if (existing != null) {
            return existing;
        }
        String name = type.simpleName() + (arguments.isEmpty() ? "" : "_" + suffix);
        if (usedNames.contains(name)) {
            String qualified = type.qualifiedName().replace('.', '_') + (arguments.isEmpty() ? "" : "_" + suffix);
            warnings.add(new Warning("SCHEMA_NAME_COLLISION",
                    "Two types are named " + name + "; using " + qualified, type.qualifiedName()));
            name = qualified;
        }
        usedNames.add(name);
        nameByKey.put(key, name);
        pending.add(new SchemaRequest(name, type, bindTypeParameters(type, arguments)));
        return name;
    }

    public void registerSynthetic(SchemaInfo schema) {
        if (usedNames.add(schema.name())) {
            synthetic.put(schema.name(), schema);
        }
    }

    public Optional<SchemaRequest> next() {
        return Optional.ofNullable(pending.poll());
    }

    public Collection<SchemaInfo> synthetic() {
        return Collections.unmodifiableCollection(synthetic.values());
    }

    public List<Warning> warnings() {
        return List.copyOf(warnings);
    }

    void addWarning(Warning warning) {
        warnings.add(warning);
    }

    static String nameOf(TypeRef type) {
        return switch (type) {
            case ScalarType scalar -> switch (scalar.kind()) {
                case STRING -> "String";
                case INTEGER -> "Integer";
                case LONG -> "Long";
                case FLOAT -> "Float";
                case DOUBLE -> "Double";
                case DECIMAL -> "BigDecimal";
                case NUMBER -> "Number";
                case BOOLEAN -> "Boolean";
                case DATE -> "LocalDate";
                case DATE_TIME -> "DateTime";
                case TIME -> "LocalTime";
                case UUID -> "UUID";
                case BINARY -> "Binary";
            };
            case ArrayOf array -> nameOf(array.items()) + "List";
            case MapOf map -> nameOf(map.values()) + "Map";
            case ObjectRef object -> object.schemaName();
            case EnumRef enumRef -> enumRef.schemaName();
            case OpaqueType opaque -> "Object";
        };
    }

    private static Map<String, TypeRef> bindTypeParameters(TypeIndex.IndexedType type, List<TypeRef> arguments) {
        if (!(type.declaration() instanceof NodeWithTypeParameters<?> generic)) {
            return Map.of();
        }
        Map<String, TypeRef> bound = new HashMap<>();
        NodeList<TypeParameter> parameters = generic.getTypeParameters();
        for (int i = 0; i < parameters.size() && i < arguments.size(); i++) {
            bound.put(parameters.get(i).getNameAsString(), arguments.get(i));
        }
        return bound;
    }
}
