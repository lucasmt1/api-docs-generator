package dev.apidocs.core.model;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SchemaGraph {

    private SchemaGraph() {
    }

    /** Schema names reachable from {@code roots} through schema fields, roots included. */
    public static Set<String> closure(Collection<String> roots, Map<String, SchemaInfo> schemas) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!seen.add(name)) {
                continue;
            }
            SchemaInfo schema = schemas.get(name);
            if (schema != null) {
                schema.fields().forEach(field -> queue.addAll(field.type().referencedSchemas()));
            }
        }
        return seen;
    }
}
