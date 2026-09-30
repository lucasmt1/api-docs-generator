package dev.apidocs.core.generation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.RequestBodyInfo;
import dev.apidocs.core.model.ResponseInfo;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.testsupport.ModelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private final ArchitectureRules rules = new ArchitectureRules();

    @Test
    void flagsEntitiesExposedByEndpoints() {
        assertThat(rules.evaluate(ModelFixtures.orderApi())).containsExactly(
                new ArchitectureAlert("ENTITY_EXPOSED", "Customer", List.of("Customer", "GET /api/orders/{id}/customer")));
    }

    @Test
    void flagsLayeringValidationDependencyAndHandlerProblems() {
        ApiModel base = ModelFixtures.orderApi();
        EndpointInfo update = new EndpointInfo("OrderController#update", HttpMethod.PUT, "/api/orders/{id}", "update",
                List.of(), new RequestBodyInfo(new ObjectRef("CreateOrderRequest"), false), new ResponseInfo(200, null),
                List.of(), List.of(), "", false, "", "", List.of(), List.of(), List.of());
        ControllerInfo controller = new ControllerInfo("OrderController", "com.shop.OrderController", "/api/orders", "",
                List.of("OrderRepository", "OrderService"), List.of(update));
        List<ServiceInfo> services = List.of(
                new ServiceInfo("AService", "com.shop.AService", List.of("BService"), List.of()),
                new ServiceInfo("BService", "com.shop.BService", List.of("AService"), List.of()),
                new ServiceInfo("BigService", "com.shop.BigService",
                        List.of("A", "B", "C", "D", "E", "F", "G", "H"), List.of()));
        ApiModel model = new ApiModel(base.project(), List.of(controller), base.schemas(), base.entities(), services,
                base.repositories(), List.of(), List.of());

        assertThat(rules.evaluate(model)).containsExactly(
                new ArchitectureAlert("CONTROLLER_USES_REPOSITORY", "OrderController",
                        List.of("OrderController", "OrderRepository")),
                new ArchitectureAlert("MISSING_VALID", "OrderController#update",
                        List.of("PUT /api/orders/{id}", "CreateOrderRequest")),
                new ArchitectureAlert("NO_EXCEPTION_HANDLER", "-", List.of()),
                new ArchitectureAlert("SERVICE_CYCLE", "AService, BService", List.of("AService, BService")),
                new ArchitectureAlert("SERVICE_TOO_MANY_DEPENDENCIES", "BigService", List.of("BigService", "8")));
    }
}
