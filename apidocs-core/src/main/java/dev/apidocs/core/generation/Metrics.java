package dev.apidocs.core.generation;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ServiceInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Metrics {

    private Metrics() {
    }

    static List<List<String>> summary(ApiModel model, Messages messages) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(messages.get("metric.controllers"), String.valueOf(model.controllers().size())));
        rows.add(List.of(messages.get("metric.endpoints"), String.valueOf(model.endpointCount())));
        rows.add(List.of(messages.get("metric.schemas"),
                String.valueOf(model.schemas().stream().filter(schema -> !schema.entity()).count())));
        rows.add(List.of(messages.get("metric.entities"), String.valueOf(model.entities().size())));
        rows.add(List.of(messages.get("metric.services"), String.valueOf(model.services().size())));
        rows.add(List.of(messages.get("metric.repositories"), String.valueOf(model.repositories().size())));
        return rows;
    }

    static List<List<String>> architecture(ApiModel model, Messages messages) {
        List<List<String>> rows = new ArrayList<>(summary(model, messages));
        String average = model.controllers().isEmpty() ? "0"
                : String.format(messages.locale(), "%.1f", (double) model.endpointCount() / model.controllers().size());
        rows.add(List.of(messages.get("metric.avgEndpoints"), average));
        String largest = model.services().stream()
                .max(Comparator.comparingInt((ServiceInfo s) -> s.methods().size())
                        .thenComparing(ServiceInfo::name, Comparator.reverseOrder()))
                .map(service -> service.name() + " (" + service.methods().size() + ")")
                .orElse(Texts.EMPTY);
        rows.add(List.of(messages.get("metric.largestService"), largest));
        return rows;
    }
}
