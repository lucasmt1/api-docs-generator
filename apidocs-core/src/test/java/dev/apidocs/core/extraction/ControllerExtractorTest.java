package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.apidocs.core.analysis.ConstantResolver;
import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.ArrayOf;
import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ParameterLocation;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControllerExtractorTest {

    private static final String CONTROLLER = """
            package com.x;
            import java.util.List;
            import org.springframework.data.domain.Page;
            import org.springframework.data.domain.Pageable;
            import org.springframework.http.HttpStatus;
            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.*;

            /** Orders API. */
            @RestController
            @RequestMapping(Paths.ORDERS)
            @Secured("ROLE_USER")
            public class OrderController {
                private final OrderService orderService;

                public OrderController(OrderService orderService) { this.orderService = orderService; }

                /** Finds one order. */
                @GetMapping("/{id}")
                public OrderView get(@PathVariable("id") Long orderId) { return orderService.find(orderId); }

                @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
                @ResponseStatus(HttpStatus.CREATED)
                @PreAuthorize("hasRole('ADMIN')")
                public OrderView create(@Valid @RequestBody CreateOrder body) { return orderService.create(body); }

                @PostMapping("/{id}/copy")
                public ResponseEntity<OrderView> copy(@PathVariable Long id) {
                    return ResponseEntity.created(null).body(this.orderService.copy(id));
                }

                @DeleteMapping("/{id}")
                public ResponseEntity<Void> delete(@PathVariable Long id) {
                    orderService.delete(id);
                    return ResponseEntity.noContent().build();
                }

                @GetMapping
                public Page<OrderView> list(Pageable pageable,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "10") @Max(50) int limit,
                        @RequestHeader("X-Tenant") String tenant) { return null; }

                @GetMapping({"/all", "/everything"})
                @Deprecated
                public List<OrderView> all() {
                    if (true) throw new IllegalStateException("x");
                    return List.of();
                }

                @RequestMapping("/ping")
                public String ping() { return "pong"; }

                public void helper() { }
            }
            """;

    private final TypeIndex index = JavaSnippets.index(
            CONTROLLER,
            "package com.x; public final class Paths { public static final String ORDERS = \"/api/orders\"; }",
            "package com.x; public class OrderService { }",
            "package com.x; public record OrderView(Long id) { }",
            "package com.x; public record CreateOrder(Long customerId) { }",
            """
            package com.x;
            @Controller
            public class PageController {
                @GetMapping("/home") public String home() { return "home"; }
                @RequestMapping("/legacy") public String legacy() { return "legacy"; }
                @GetMapping("/api/health") @ResponseBody public String health() { return "ok"; }
            }
            """);
    private final List<Warning> warnings = new ArrayList<>();
    private final List<ControllerInfo> controllers = new ControllerExtractor(index,
            new TypeResolver(index, new SchemaRegistry()), new ConstantResolver(index), "/shop", Set.of("OrderService"))
            .extract(warnings);

    private EndpointInfo endpoint(String id) {
        return controllers.stream().flatMap(c -> c.endpoints().stream())
                .filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void findsRestControllersAndResponseBodyMethods() {
        assertThat(controllers).extracting(ControllerInfo::name).containsExactly("OrderController", "PageController");
        ControllerInfo orders = controllers.get(0);
        assertThat(orders.basePath()).isEqualTo("/shop/api/orders");
        assertThat(orders.description()).isEqualTo("Orders API.");
        assertThat(orders.dependencies()).containsExactly("OrderService");
        assertThat(orders.endpoints()).extracting(EndpointInfo::id).containsExactly(
                "OrderController#get", "OrderController#create", "OrderController#copy", "OrderController#delete",
                "OrderController#list", "OrderController#all", "OrderController#all#2", "OrderController#ping");
        assertThat(controllers.get(1).endpoints()).singleElement()
                .satisfies(e -> assertThat(e.path()).isEqualTo("/shop/api/health"));
        assertThat(warnings).extracting(Warning::code).containsExactly("AMBIGUOUS_HTTP_METHOD");
    }

    @Test
    void warnsOncePerDocumentedHandlerMappedWithoutAnHttpMethod() {
        assertThat(warnings).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("AMBIGUOUS_HTTP_METHOD");
            assertThat(warning.location()).isEqualTo("com.x.OrderController#ping");
            assertThat(warning.message()).contains("GET");
        });
    }

    @Test
    void extractsPathVariablesSecurityAndServiceCalls() {
        EndpointInfo get = endpoint("OrderController#get");

        assertThat(get.method()).isEqualTo(HttpMethod.GET);
        assertThat(get.path()).isEqualTo("/shop/api/orders/{id}");
        assertThat(get.parameters()).singleElement().isEqualTo(new ParameterInfo("id", ParameterLocation.PATH,
                new ScalarType(ScalarKind.LONG), true, "", List.of()));
        assertThat(get.response().status()).isEqualTo(200);
        assertThat(get.response().body()).isEqualTo(new ObjectRef("OrderView"));
        assertThat(get.security()).isEqualTo("roles: ROLE_USER");
        assertThat(get.description()).isEqualTo("Finds one order.");
        assertThat(get.serviceCalls()).containsExactly(new MethodRef("OrderService", "find"));
    }

    @Test
    void extractsRequestBodiesAndSuccessStatuses() {
        EndpointInfo create = endpoint("OrderController#create");
        assertThat(create.method()).isEqualTo(HttpMethod.POST);
        assertThat(create.path()).isEqualTo("/shop/api/orders");
        assertThat(create.response().status()).isEqualTo(201);
        assertThat(create.requestBody().type()).isEqualTo(new ObjectRef("CreateOrder"));
        assertThat(create.requestBody().validated()).isTrue();
        assertThat(create.security()).isEqualTo("hasRole('ADMIN')");
        assertThat(create.consumes()).containsExactly("application/json");

        EndpointInfo copy = endpoint("OrderController#copy");
        assertThat(copy.response().status()).isEqualTo(201);
        assertThat(copy.response().body()).isEqualTo(new ObjectRef("OrderView"));
        assertThat(copy.serviceCalls()).containsExactly(new MethodRef("OrderService", "copy"));

        EndpointInfo delete = endpoint("OrderController#delete");
        assertThat(delete.response().status()).isEqualTo(204);
        assertThat(delete.response().hasBody()).isFalse();
    }

    @Test
    void extractsQueryHeaderAndPageableParameters() {
        EndpointInfo list = endpoint("OrderController#list");

        assertThat(list.parameters()).extracting(ParameterInfo::name)
                .containsExactly("page", "size", "sort", "status", "limit", "X-Tenant");
        ParameterInfo status = list.parameters().get(3);
        assertThat(status.required()).isFalse();
        ParameterInfo limit = list.parameters().get(4);
        assertThat(limit.required()).isFalse();
        assertThat(limit.defaultValue()).isEqualTo("10");
        assertThat(limit.constraints()).extracting(Constraint::describe).containsExactly("@Max(value=50)");
        ParameterInfo tenant = list.parameters().get(5);
        assertThat(tenant.in()).isEqualTo(ParameterLocation.HEADER);
        assertThat(tenant.required()).isTrue();
        assertThat(list.response().body()).isEqualTo(new ObjectRef("PagedModel_OrderView"));
    }

    @Test
    void handlesMultiplePathsDeprecationThrowsAndAnyMethod() {
        EndpointInfo all = endpoint("OrderController#all");
        EndpointInfo everything = endpoint("OrderController#all#2");
        assertThat(all.path()).isEqualTo("/shop/api/orders/all");
        assertThat(everything.path()).isEqualTo("/shop/api/orders/everything");
        assertThat(all.deprecated()).isTrue();
        assertThat(all.response().body()).isEqualTo(new ArrayOf(new ObjectRef("OrderView")));
        assertThat(all.thrownExceptions()).containsExactly("IllegalStateException");

        EndpointInfo ping = endpoint("OrderController#ping");
        assertThat(ping.method()).isEqualTo(HttpMethod.ANY);
        assertThat(ping.response().body()).isEqualTo(new ScalarType(ScalarKind.STRING));
    }

    private static EndpointInfo onlyEndpointOf(String methodSource) {
        TypeIndex snippetIndex = JavaSnippets.index("""
                package com.y;
                import org.springframework.http.HttpHeaders;
                import org.springframework.web.bind.annotation.*;
                @RestController
                public class SnippetController {
                """ + methodSource + """
                }
                """);
        return new ControllerExtractor(snippetIndex, new TypeResolver(snippetIndex, new SchemaRegistry()),
                new ConstantResolver(snippetIndex), "", Set.of())
                .extract(new ArrayList<>()).get(0).endpoints().get(0);
    }

    @Test
    void anUnresolvableDefaultValueStillMakesParametersOptional() {
        EndpointInfo endpoint = onlyEndpointOf("""
                @GetMapping("/search")
                public String search(@RequestParam(defaultValue = Library.DEFAULT_TERM) String term,
                        @RequestHeader(name = "X-Lang", defaultValue = Library.LANG) String lang,
                        @RequestParam(defaultValue = "5") int size,
                        @RequestParam String required) { return ""; }
                """);

        assertThat(endpoint.parameters()).extracting(ParameterInfo::name, ParameterInfo::required,
                ParameterInfo::defaultValue).containsExactly(
                        tuple("term", false, ""),
                        tuple("X-Lang", false, ""),
                        tuple("size", false, "5"),
                        tuple("required", true, ""));
    }

    @Test
    void skipsHttpHeadersBoundWithRequestHeader() {
        EndpointInfo endpoint = onlyEndpointOf("""
                @GetMapping("/echo")
                public String echo(@RequestHeader HttpHeaders headers, @RequestHeader("X-Id") String id) {
                    return "";
                }
                """);

        assertThat(endpoint.parameters()).extracting(ParameterInfo::name).containsExactly("X-Id");
    }

    @Test
    void controllersSharingASimpleNameAreQualifiedAndReported() {
        TypeIndex shared = JavaSnippets.index(
                "package demo.v1; @RestController public class UserController { @GetMapping(\"/v1/users\") public String list() { return \"\"; } }",
                "package demo.v2; @RestController public class UserController { @GetMapping(\"/v2/users\") public String list() { return \"\"; } }",
                "package demo.v2; @RestController public class OrderController { @GetMapping(\"/v2/orders\") public String list() { return \"\"; } }",
                "package demo.web; @Controller public class OrderController { @GetMapping(\"/orders\") public String page() { return \"orders\"; } }");
        List<Warning> found = new ArrayList<>();

        List<ControllerInfo> extracted = new ControllerExtractor(shared, new TypeResolver(shared, new SchemaRegistry()),
                new ConstantResolver(shared), "", Set.of()).extract(found);

        assertThat(extracted).extracting(ControllerInfo::name)
                .containsExactly("demo_v1_UserController", "OrderController", "demo_v2_UserController");
        assertThat(extracted).flatExtracting(ControllerInfo::endpoints).extracting(EndpointInfo::id)
                .containsExactly("demo_v1_UserController#list", "OrderController#list", "demo_v2_UserController#list");
        assertThat(found).extracting(Warning::code, Warning::location).containsExactly(
                tuple("CONTROLLER_NAME_COLLISION", "demo.v1.UserController"),
                tuple("CONTROLLER_NAME_COLLISION", "demo.v2.UserController"));
    }

    @Test
    void joinsPathsNormalizingSlashes() {
        assertThat(ControllerExtractor.joinPaths("", "/api/", "/orders/")).isEqualTo("/api/orders");
        assertThat(ControllerExtractor.joinPaths("", "", "")).isEqualTo("/");
        assertThat(ControllerExtractor.joinPaths("/shop", "api", "{id}")).isEqualTo("/shop/api/{id}");
    }
}
