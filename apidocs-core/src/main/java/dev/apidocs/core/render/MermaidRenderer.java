package dev.apidocs.core.render;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EntityField;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.Relationship;
import dev.apidocs.core.model.RepositoryInfo;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.TypeRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic Mermaid diagrams built from the extracted model (no LLM involved). */
public final class MermaidRenderer {

    static final int GROUPING_THRESHOLD = 40;

    private record Component(String layer, String prefix, String name, String packageName) {
    }

    public String layers(ApiModel model) {
        List<Component> components = new ArrayList<>();
        model.controllers().forEach(c -> components.add(new Component("Controllers", "C_", c.name(), packageOf(c.qualifiedName()))));
        model.services().forEach(s -> components.add(new Component("Services", "S_", s.name(), packageOf(s.qualifiedName()))));
        model.repositories().forEach(r -> components.add(new Component("Repositories", "R_", r.name(), packageOf(r.qualifiedName()))));
        model.entities().forEach(e -> components.add(new Component("Entities", "E_", e.name(), packageOf(e.qualifiedName()))));
        boolean grouped = components.size() > GROUPING_THRESHOLD;

        Map<String, String> nodeOf = new HashMap<>();
        Map<String, Map<String, String>> nodesByLayer = new LinkedHashMap<>();
        Map<String, Integer> groupSizes = new HashMap<>();
        for (Component component : components) {
            String nodeId = grouped ? component.prefix() + sanitize(component.packageName())
                    : component.prefix() + component.name();
            nodeOf.put(component.prefix() + component.name(), nodeId);
            groupSizes.merge(nodeId, 1, Integer::sum);
            nodesByLayer.computeIfAbsent(component.layer(), layer -> new LinkedHashMap<>())
                    .putIfAbsent(nodeId, grouped ? component.packageName() : component.name());
        }

        StringBuilder out = new StringBuilder("flowchart LR\n");
        nodesByLayer.forEach((layer, nodes) -> {
            out.append("  subgraph ").append(layer).append('\n');
            nodes.forEach((id, label) -> out.append("    ").append(id).append("[\"")
                    .append(grouped ? label + " (" + groupSizes.get(id) + ")" : label).append("\"]\n"));
            out.append("  end\n");
        });

        Set<String> edges = new LinkedHashSet<>();
        for (ControllerInfo controller : model.controllers()) {
            controller.dependencies().forEach(dep -> link(edges, nodeOf, "C_" + controller.name(), dep));
        }
        for (ServiceInfo service : model.services()) {
            service.dependencies().forEach(dep -> link(edges, nodeOf, "S_" + service.name(), dep));
        }
        for (RepositoryInfo repository : model.repositories()) {
            addEdge(edges, nodeOf.get("R_" + repository.name()), nodeOf.get("E_" + repository.entity()));
        }
        edges.forEach(edge -> out.append("  ").append(edge).append('\n'));
        return out.toString();
    }

    public String entities(List<EntityInfo> entities) {
        Set<String> names = entities.stream().map(EntityInfo::name).collect(Collectors.toSet());
        StringBuilder out = new StringBuilder("erDiagram\n");
        for (EntityInfo entity : entities) {
            if (entity.fields().isEmpty()) {
                continue;
            }
            out.append("  ").append(entity.name()).append(" {\n");
            for (EntityField field : entity.fields()) {
                out.append("    ").append(attributeType(field.type())).append(' ').append(attributeName(field.name()))
                        .append(field.id() ? " PK" : field.unique() ? " UK" : "").append('\n');
            }
            out.append("  }\n");
        }
        for (EntityInfo entity : entities) {
            for (Relationship relationship : entity.relationships()) {
                if (!names.contains(relationship.target()) || !relationship.mappedBy().isEmpty()) {
                    continue;
                }
                String line = switch (relationship.kind()) {
                    case MANY_TO_ONE -> relationship.target() + " ||--o{ " + entity.name();
                    case ONE_TO_MANY -> entity.name() + " ||--o{ " + relationship.target();
                    case ONE_TO_ONE -> entity.name() + " ||--|| " + relationship.target();
                    case MANY_TO_MANY -> entity.name() + " }o--o{ " + relationship.target();
                };
                out.append("  ").append(line).append(" : ").append(attributeName(relationship.field())).append('\n');
            }
        }
        return out.toString();
    }

    private static void link(Set<String> edges, Map<String, String> nodeOf, String fromKey, String dependency) {
        String target = nodeOf.containsKey("S_" + dependency) ? nodeOf.get("S_" + dependency) : nodeOf.get("R_" + dependency);
        addEdge(edges, nodeOf.get(fromKey), target);
    }

    private static void addEdge(Set<String> edges, String from, String to) {
        if (from != null && to != null && !from.equals(to)) {
            edges.add(from + " --> " + to);
        }
    }

    private static String attributeType(TypeRef type) {
        return switch (type) {
            case ScalarType scalar -> scalar.kind().name().toLowerCase(Locale.ROOT);
            case EnumRef enumRef -> "enum";
            default -> "object";
        };
    }

    private static String attributeName(String name) {
        return name.replace('.', '_');
    }

    static String sanitize(String text) {
        return text.isEmpty() ? "default" : text.replaceAll("[^A-Za-z0-9]", "_");
    }

    static String packageOf(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }
}
