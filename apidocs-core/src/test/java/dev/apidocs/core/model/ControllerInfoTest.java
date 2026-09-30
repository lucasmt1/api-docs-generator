package dev.apidocs.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControllerInfoTest {

    @Test
    void withEndpointsReplacesOnlyTheEndpoints() {
        EndpointInfo endpoint = new EndpointInfo("getOrder", HttpMethod.GET, "/orders/{id}", "getOrder",
                List.of(), null, new ResponseInfo(200, null), List.of(), List.of(), null, false, null, null,
                List.of(), List.of(), List.of());
        ControllerInfo controller = new ControllerInfo("OrderController", "x.OrderController", "/orders",
                "Orders", List.of("OrderService"), List.of());

        List<EndpointInfo> newEndpoints = new ArrayList<>(List.of(endpoint));
        ControllerInfo updated = controller.withEndpoints(newEndpoints);
        newEndpoints.clear();

        assertThat(updated.endpoints()).containsExactly(endpoint);
        assertThat(updated.name()).isEqualTo("OrderController");
        assertThat(updated.qualifiedName()).isEqualTo("x.OrderController");
        assertThat(updated.basePath()).isEqualTo("/orders");
        assertThat(updated.description()).isEqualTo("Orders");
        assertThat(updated.dependencies()).containsExactly("OrderService");
        assertThat(controller.endpoints()).isEmpty();
    }
}
