package dev.apidocs.core.generation;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.RepositoryInfo;
import dev.apidocs.core.model.SchemaGraph;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.ServiceInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Architectural checks that never depend on the LLM: the LLM only comments on what these rules find. */
public final class ArchitectureRules {

    public static final int MAX_SERVICE_DEPENDENCIES = 7;

    public List<ArchitectureAlert> evaluate(ApiModel model) {
        List<ArchitectureAlert> alerts = new ArrayList<>();
        Map<String, SchemaInfo> schemas = model.schemasByName();
        Set<String> repositories = model.repositories().stream().map(RepositoryInfo::name).collect(Collectors.toSet());
        Set<String> services = model.services().stream().map(ServiceInfo::name).collect(Collectors.toSet());

        for (ControllerInfo controller : model.controllers()) {
            for (EndpointInfo endpoint : controller.endpoints()) {
                String label = endpoint.method() + " " + endpoint.path();
                Set<String> referenced = new LinkedHashSet<>();
                if (endpoint.requestBody() != null) {
                    referenced.addAll(SchemaGraph.closure(endpoint.requestBody().type().referencedSchemas(), schemas));
                }
                if (endpoint.response().hasBody()) {
                    referenced.addAll(SchemaGraph.closure(endpoint.response().body().referencedSchemas(), schemas));
                }
                referenced.stream().map(schemas::get).filter(Objects::nonNull).filter(SchemaInfo::entity)
                        .forEach(entity -> alerts.add(new ArchitectureAlert("ENTITY_EXPOSED", entity.name(),
                                List.of(entity.name(), label))));
                if (endpoint.requestBody() != null && !endpoint.requestBody().validated()) {
                    endpoint.requestBody().type().referencedSchemas().stream()
                            .map(schemas::get).filter(Objects::nonNull)
                            .filter(schema -> schema.fields().stream().anyMatch(f -> !f.constraints().isEmpty()))
                            .findFirst()
                            .ifPresent(schema -> alerts.add(new ArchitectureAlert("MISSING_VALID", endpoint.id(),
                                    List.of(label, schema.name()))));
                }
            }
            controller.dependencies().stream().filter(repositories::contains)
                    .forEach(repository -> alerts.add(new ArchitectureAlert("CONTROLLER_USES_REPOSITORY",
                            controller.name(), List.of(controller.name(), repository))));
        }
        for (ServiceInfo service : model.services()) {
            if (service.dependencies().size() > MAX_SERVICE_DEPENDENCIES) {
                alerts.add(new ArchitectureAlert("SERVICE_TOO_MANY_DEPENDENCIES", service.name(),
                        List.of(service.name(), String.valueOf(service.dependencies().size()))));
            }
        }
        for (List<String> cycle : serviceCycles(model.services(), services)) {
            String members = String.join(", ", cycle);
            alerts.add(new ArchitectureAlert("SERVICE_CYCLE", members, List.of(members)));
        }
        if (!model.controllers().isEmpty() && model.exceptionMappings().isEmpty()) {
            alerts.add(new ArchitectureAlert("NO_EXCEPTION_HANDLER", "-", List.of()));
        }
        return alerts.stream().distinct()
                .sorted(Comparator.comparing(ArchitectureAlert::code)
                        .thenComparing(ArchitectureAlert::subject)
                        .thenComparing(alert -> String.join("|", alert.arguments())))
                .toList();
    }

    private static List<List<String>> serviceCycles(List<ServiceInfo> services, Set<String> serviceNames) {
        Map<String, List<String>> graph = new TreeMap<>();
        services.forEach(service -> graph.put(service.name(),
                service.dependencies().stream().filter(serviceNames::contains).toList()));
        return new Tarjan(graph).components().stream()
                .filter(component -> component.size() > 1 || graph.get(component.get(0)).contains(component.get(0)))
                .map(component -> component.stream().sorted().toList())
                .sorted(Comparator.comparing(component -> component.get(0)))
                .toList();
    }

    /** Strongly connected components (Tarjan). */
    private static final class Tarjan {

        private final Map<String, List<String>> graph;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();
        private int counter;

        Tarjan(Map<String, List<String>> graph) {
            this.graph = graph;
        }

        List<List<String>> components() {
            for (String node : graph.keySet()) {
                if (!index.containsKey(node)) {
                    visit(node);
                }
            }
            return components;
        }

        private void visit(String node) {
            index.put(node, counter);
            low.put(node, counter);
            counter++;
            stack.push(node);
            onStack.add(node);
            for (String next : graph.getOrDefault(node, List.of())) {
                if (!index.containsKey(next)) {
                    visit(next);
                    low.put(node, Math.min(low.get(node), low.get(next)));
                } else if (onStack.contains(next)) {
                    low.put(node, Math.min(low.get(node), index.get(next)));
                }
            }
            if (low.get(node).equals(index.get(node))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }
    }
}
