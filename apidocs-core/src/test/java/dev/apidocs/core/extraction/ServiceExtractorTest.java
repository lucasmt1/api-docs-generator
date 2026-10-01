package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.apidocs.core.analysis.TypeIndex;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.ServiceMethod;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceExtractorTest {

    private final TypeIndex index = JavaSnippets.index(
            """
            package com.x;
            import org.springframework.beans.factory.annotation.Autowired;
            @Service
            @Transactional
            public class OrderService {
                private final OrderRepository orderRepository;
                private final CustomerService customerService;
                @Autowired private AuditService audit;
                private int counter;

                public OrderService(OrderRepository orderRepository, CustomerService customerService) {
                    this.orderRepository = orderRepository;
                    this.customerService = customerService;
                }

                /** Creates an order. */
                public Order create(Long customerId) {
                    customerService.check(customerId);
                    Order order = orderRepository.findById(customerId).orElseThrow(() -> new NotFoundException("x"));
                    if (order == null) throw new InvalidStateException();
                    return orderRepository.save(order);
                }

                @Transactional(readOnly = true)
                public Order find(Long id) { return load(id); }

                public void remove(Long id) throws AuditException { this.helper(id); }

                private Order load(Long id) { return orderRepository.findById(id).orElseThrow(NotFoundException::new); }

                private void helper(Long id) { throw new IllegalArgumentException("x"); }
            }
            """,
            "package com.x; @Service public class CustomerService { public void check(Long id) {} }",
            "package com.x; @Service @RequiredArgsConstructor public class AuditService { private final Clock clock; private String name; }",
            "package com.x; interface OrderRepository {}");
    private final List<ServiceInfo> services =
            new ServiceExtractor(index, ServiceExtractor.serviceNames(index), Set.of("OrderRepository")).extract();

    private ServiceInfo service(String name) {
        return services.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void reportsServicesThatShareASimpleName() {
        TypeIndex shared = JavaSnippets.index(
                "package a; @Service public class UserService { }",
                "package b; @Service public class UserService { }",
                "package b; @Service public class MailService { }",
                "package c; public class MailService { }");

        assertThat(ServiceExtractor.nameCollisions(shared)).extracting(Warning::code, Warning::location)
                .containsExactly(tuple("SERVICE_NAME_COLLISION", "a.UserService"),
                        tuple("SERVICE_NAME_COLLISION", "b.UserService"));
        assertThat(ServiceExtractor.nameCollisions(index)).isEmpty();
    }

    @Test
    void findsServicesAndTheirDependencies() {
        assertThat(ServiceExtractor.serviceNames(index)).containsExactly("AuditService", "CustomerService", "OrderService");
        assertThat(service("OrderService").dependencies())
                .containsExactly("AuditService", "CustomerService", "OrderRepository");
        assertThat(service("AuditService").dependencies()).containsExactly("Clock");
    }

    @Test
    void extractsPublicMethodsWithBodiesExceptionsAndCalls() {
        ServiceInfo orders = service("OrderService");
        assertThat(orders.methods()).extracting(ServiceMethod::name).containsExactly("create", "find", "remove");

        ServiceMethod create = orders.method("create").orElseThrow();
        assertThat(create.signature()).isEqualTo("Order create(Long customerId)");
        assertThat(create.description()).isEqualTo("Creates an order.");
        assertThat(create.transactional()).isTrue();
        assertThat(create.readOnly()).isFalse();
        assertThat(create.body()).startsWith("{").contains("customerService.check(customerId);").doesNotContain("\r");
        assertThat(create.thrownExceptions()).containsExactly("NotFoundException", "InvalidStateException");
        assertThat(create.calls()).containsExactly(new MethodRef("CustomerService", "check"),
                new MethodRef("OrderRepository", "findById"), new MethodRef("OrderRepository", "save"));
    }

    @Test
    void followsSameClassHelpersForExceptionsAndCalls() {
        ServiceInfo orders = service("OrderService");

        ServiceMethod find = orders.method("find").orElseThrow();
        assertThat(find.readOnly()).isTrue();
        assertThat(find.thrownExceptions()).containsExactly("NotFoundException");
        assertThat(find.calls()).containsExactly(new MethodRef("OrderRepository", "findById"));

        ServiceMethod remove = orders.method("remove").orElseThrow();
        assertThat(remove.signature()).isEqualTo("void remove(Long id) throws AuditException");
        assertThat(remove.thrownExceptions()).containsExactly("AuditException", "IllegalArgumentException");
    }
}
