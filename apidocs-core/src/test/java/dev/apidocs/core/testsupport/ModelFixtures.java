package dev.apidocs.core.testsupport;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.EntityField;
import dev.apidocs.core.model.EntityInfo;
import dev.apidocs.core.model.EnumRef;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.FieldInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ParameterLocation;
import dev.apidocs.core.model.ProjectInfo;
import dev.apidocs.core.model.RelationKind;
import dev.apidocs.core.model.Relationship;
import dev.apidocs.core.model.RepositoryInfo;
import dev.apidocs.core.model.RequestBodyInfo;
import dev.apidocs.core.model.ResponseInfo;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.model.SchemaKind;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.ServiceMethod;
import java.util.List;
import java.util.Map;

/** A small, complete model: 1 controller, 3 endpoints, 5 schemas, 2 entities, 1 service. */
public final class ModelFixtures {

    private ModelFixtures() {
    }

    public static ApiModel orderApi() {
        ProjectInfo project = new ProjectInfo("Orders API", "Handles orders.", "1.0.0", "21", "4.1.1",
                List.of("spring-boot-starter-webmvc"), "orders-api", "", "maven");
        ParameterInfo id = new ParameterInfo("id", ParameterLocation.PATH, new ScalarType(ScalarKind.LONG), true, "",
                List.of());
        EndpointInfo create = new EndpointInfo("OrderController#create", HttpMethod.POST, "/api/orders", "create",
                List.of(), new RequestBodyInfo(new ObjectRef("CreateOrderRequest"), true),
                new ResponseInfo(201, new ObjectRef("OrderResponse")),
                List.of(new ErrorResponse(400, "MethodArgumentNotValidException"),
                        new ErrorResponse(404, "NotFoundException"),
                        new ErrorResponse(409, "InsufficientStockException")),
                List.of(), "hasRole('USER')", false, "", "Creates an order.", List.of(), List.of(),
                List.of(new MethodRef("OrderService", "create")));
        EndpointInfo get = new EndpointInfo("OrderController#get", HttpMethod.GET, "/api/orders/{id}", "get",
                List.of(id), null, new ResponseInfo(200, new ObjectRef("OrderResponse")),
                List.of(new ErrorResponse(404, "NotFoundException")), List.of(), "", false, "", "", List.of(),
                List.of(), List.of(new MethodRef("OrderService", "find")));
        EndpointInfo customer = new EndpointInfo("OrderController#customer", HttpMethod.GET,
                "/api/orders/{id}/customer", "customer", List.of(id), null,
                new ResponseInfo(200, new ObjectRef("Customer")), List.of(), List.of(), "", true, "", "", List.of(),
                List.of(), List.of());
        ControllerInfo controller = new ControllerInfo("OrderController", "com.shop.OrderController", "/api/orders",
                "Order management.", List.of("OrderService"), List.of(create, get, customer));

        List<SchemaInfo> schemas = List.of(
                new SchemaInfo("CreateOrderRequest", "com.shop.CreateOrderRequest", SchemaKind.OBJECT, List.of(
                        new FieldInfo("customerId", new ScalarType(ScalarKind.LONG), true,
                                List.of(Constraint.of("NotNull")), ""),
                        new FieldInfo("items", new ArrayOf(new ObjectRef("OrderItemRequest")), true,
                                List.of(Constraint.of("NotEmpty"), Constraint.of("Size", "max", "50")), "Items to buy.")),
                        List.of(), "Data to place an order.", false),
                new SchemaInfo("Customer", "com.shop.Customer", SchemaKind.OBJECT, List.of(
                        new FieldInfo("id", new ScalarType(ScalarKind.LONG), false, List.of(), ""),
                        new FieldInfo("name", new ScalarType(ScalarKind.STRING), false, List.of(), "")),
                        List.of(), "", true),
                new SchemaInfo("OrderItemRequest", "com.shop.OrderItemRequest", SchemaKind.OBJECT, List.of(
                        new FieldInfo("productId", new ScalarType(ScalarKind.LONG), true,
                                List.of(Constraint.of("NotNull")), ""),
                        new FieldInfo("quantity", new ScalarType(ScalarKind.INTEGER), true,
                                List.of(new Constraint("Min", Map.of("value", "1")),
                                        new Constraint("Max", Map.of("value", "10"))), "")),
                        List.of(), "", false),
                new SchemaInfo("OrderResponse", "com.shop.OrderResponse", SchemaKind.OBJECT, List.of(
                        new FieldInfo("id", new ScalarType(ScalarKind.LONG), false, List.of(), ""),
                        new FieldInfo("status", new EnumRef("OrderStatus"), false, List.of(), ""),
                        new FieldInfo("total", new ScalarType(ScalarKind.DECIMAL), false, List.of(), "")),
                        List.of(), "", false),
                new SchemaInfo("OrderStatus", "com.shop.OrderStatus", SchemaKind.ENUM, List.of(),
                        List.of("CREATED", "PAID"), "Lifecycle of an order.", false));

        List<EntityInfo> entities = List.of(
                new EntityInfo("Customer", "com.shop.Customer", "customers", List.of(
                        new EntityField("id", new ScalarType(ScalarKind.LONG), "id", true, false, false, 0),
                        new EntityField("name", new ScalarType(ScalarKind.STRING), "name", false, false, false, 120),
                        new EntityField("email", new ScalarType(ScalarKind.STRING), "email", false, true, true, 0)),
                        List.of()),
                new EntityInfo("Order", "com.shop.Order", "orders", List.of(
                        new EntityField("id", new ScalarType(ScalarKind.LONG), "id", true, false, false, 0),
                        new EntityField("status", new EnumRef("OrderStatus"), "status", false, false, false, 20),
                        new EntityField("total", new ScalarType(ScalarKind.DECIMAL), "total", false, false, false, 0)),
                        List.of(new Relationship("customer", RelationKind.MANY_TO_ONE, "Customer", ""))));

        ServiceInfo service = new ServiceInfo("OrderService", "com.shop.OrderService", List.of("OrderRepository"), List.of(
                new ServiceMethod("create", "OrderResponse create(CreateOrderRequest request)",
                        "{\n    if (stock < quantity) {\n        throw new InsufficientStockException();\n    }\n    return repository.save(order);\n}",
                        true, false, List.of("NotFoundException", "InsufficientStockException"),
                        List.of(new MethodRef("OrderRepository", "save")), "Creates an order."),
                new ServiceMethod("find", "OrderResponse find(Long id)", "{\n    return repository.findById(id);\n}",
                        true, true, List.of("NotFoundException"), List.of(new MethodRef("OrderRepository", "findById")), "")));

        return new ApiModel(project, List.of(controller), schemas, entities, List.of(service),
                List.of(new RepositoryInfo("CustomerRepository", "com.shop.CustomerRepository", "Customer", "Long"),
                        new RepositoryInfo("OrderRepository", "com.shop.OrderRepository", "Order", "Long")),
                List.of(new ExceptionMapping("InsufficientStockException", 409),
                        new ExceptionMapping("MethodArgumentNotValidException", 400),
                        new ExceptionMapping("NotFoundException", 404)),
                List.of());
    }
}
