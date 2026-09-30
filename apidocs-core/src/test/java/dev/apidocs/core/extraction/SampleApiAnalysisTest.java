package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.RelationKind;
import dev.apidocs.core.model.Relationship;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.source.SourceLimits;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SampleApiAnalysisTest {

    private static final ApiModel MODEL =
            new ModelExtractor().extract(Path.of("../examples/sample-api"), List.of(), SourceLimits.DEFAULT);

    private static EndpointInfo endpoint(String id) {
        return MODEL.controllers().stream().flatMap(c -> c.endpoints().stream())
                .filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static SchemaInfo schema(String name) {
        return MODEL.schemasByName().get(name);
    }

    @Test
    void findsControllersEndpointsAndNoWarnings() {
        assertThat(MODEL.project().name()).isEqualTo("Shop API");
        assertThat(MODEL.controllers()).extracting(ControllerInfo::name)
                .containsExactly("CustomerController", "OrderController", "ProductController");
        assertThat(MODEL.endpointCount()).isEqualTo(13);
        assertThat(MODEL.controllers().get(1).endpoints()).extracting(EndpointInfo::id).containsExactly(
                "OrderController#list", "OrderController#create", "OrderController#listByCustomer",
                "OrderController#get", "OrderController#delete", "OrderController#cancel",
                "OrderController#updateStatus");
        assertThat(MODEL.warnings()).isEmpty();
    }

    @Test
    void resolvesBodiesStatusesAndErrorsOfCreateOrder() {
        EndpointInfo create = endpoint("OrderController#create");

        assertThat(create.method()).isEqualTo(HttpMethod.POST);
        assertThat(create.path()).isEqualTo("/shop/api/orders");
        assertThat(create.response().status()).isEqualTo(201);
        assertThat(create.response().body()).isEqualTo(new ObjectRef("OrderResponse"));
        assertThat(create.requestBody().type()).isEqualTo(new ObjectRef("CreateOrderRequest"));
        assertThat(create.requestBody().validated()).isTrue();
        assertThat(create.errors()).containsExactly(
                new ErrorResponse(400, "MethodArgumentNotValidException"),
                new ErrorResponse(404, "NotFoundException"),
                new ErrorResponse(409, "InsufficientStockException"));
    }

    @Test
    void followsPrivateHelpersAndMostSpecificExceptionHandlers() {
        assertThat(endpoint("OrderController#cancel").errors()).containsExactly(
                new ErrorResponse(404, "NotFoundException"),
                new ErrorResponse(422, "InvalidOrderStateException"));
        EndpointInfo delete = endpoint("OrderController#delete");
        assertThat(delete.response().status()).isEqualTo(204);
        assertThat(delete.response().hasBody()).isFalse();
        assertThat(delete.security()).isEqualTo("hasRole('ADMIN')");
        assertThat(endpoint("OrderController#list").errors())
                .containsExactly(new ErrorResponse(400, "HandlerMethodValidationException"));
        assertThat(endpoint("ProductController#create").errors()).containsExactly(
                new ErrorResponse(400, "MethodArgumentNotValidException"),
                new ErrorResponse(409, "DuplicateResourceException"));
    }

    @Test
    void describesPagingDeprecationAndExposedEntities() {
        EndpointInfo products = endpoint("ProductController#list");
        assertThat(products.parameters()).extracting(ParameterInfo::name).containsExactly("page", "size", "sort");
        assertThat(products.response().body()).isEqualTo(new ObjectRef("PagedModel_ProductResponse"));

        EndpointInfo byCustomer = endpoint("OrderController#listByCustomer");
        assertThat(byCustomer.deprecated()).isTrue();
        assertThat(byCustomer.response().body()).isEqualTo(new ArrayOf(new ObjectRef("OrderResponse")));

        assertThat(endpoint("CustomerController#get").response().body()).isEqualTo(new ObjectRef("Customer"));
        assertThat(schema("Customer").entity()).isTrue();
        assertThat(schema("Customer").fields()).extracting(FieldInfo::name)
                .containsExactly("id", "createdAt", "updatedAt", "name", "email");
    }

    @Test
    void extractsSchemas() {
        assertThat(MODEL.schemas()).extracting(SchemaInfo::name).containsExactly(
                "CreateCustomerRequest", "CreateOrderRequest", "CreateProductRequest", "Customer", "CustomerResponse",
                "OrderItemRequest", "OrderItemResponse", "OrderResponse", "OrderStatus", "PageMetadata",
                "PageResponse_OrderResponse", "PagedModel_ProductResponse", "ProductResponse",
                "StockAdjustmentRequest", "UpdateOrderStatusRequest");
        assertThat(schema("PageResponse_OrderResponse").fields().get(0).type())
                .isEqualTo(new ArrayOf(new ObjectRef("OrderResponse")));
        assertThat(schema("OrderStatus").enumValues()).containsExactly("CREATED", "PAID", "SHIPPED", "DELIVERED", "CANCELLED");
    }

    @Test
    void extractsEntitiesServicesRepositoriesAndExceptionMappings() {
        assertThat(MODEL.entities()).extracting(EntityInfo::name)
                .containsExactly("Customer", "Order", "OrderItem", "Product");
        EntityInfo order = MODEL.entities().get(1);
        assertThat(order.table()).isEqualTo("orders");
        assertThat(order.relationships()).containsExactly(
                new Relationship("customer", RelationKind.MANY_TO_ONE, "Customer", ""),
                new Relationship("items", RelationKind.ONE_TO_MANY, "OrderItem", "order"));

        assertThat(MODEL.services()).extracting(ServiceInfo::name)
                .containsExactly("CustomerService", "OrderService", "ProductService");
        assertThat(MODEL.service("OrderService").orElseThrow().dependencies())
                .containsExactly("CustomerService", "OrderRepository", "ProductRepository");
        assertThat(MODEL.repositories()).hasSize(3);
        assertThat(MODEL.exceptionMappings()).containsExactly(
                new ExceptionMapping("BusinessException", 409),
                new ExceptionMapping("InvalidOrderStateException", 422),
                new ExceptionMapping("MethodArgumentNotValidException", 400),
                new ExceptionMapping("NotFoundException", 404));
    }
}
