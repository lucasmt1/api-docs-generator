package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConstantResolverTest {

    private final List<ParsedUnit> units = JavaSnippets.parse(
            """
            package com.x.api;
            public final class ApiPaths {
                public static final String API = "/api";
                public static final String ORDERS = API + "/orders";
                public static final String LOOP_A = LOOP_B;
                public static final String LOOP_B = LOOP_A;
            }
            """,
            """
            package com.x.web;
            import com.x.api.ApiPaths;
            import static com.x.api.ApiPaths.API;
            @RequestMapping(ApiPaths.ORDERS)
            class C {
                static final String LOCAL = "/local";
                @GetMapping(LOCAL + "/{id}") void a() {}
                @GetMapping(API) void b() {}
                @GetMapping(ApiPaths.LOOP_A) void c() {}
                @GetMapping(SomeLib.PATH) void d() {}
            }
            """);
    private final ConstantResolver resolver = new ConstantResolver(TypeIndex.build(units));
    private final ParsedUnit web = units.get(1);
    private final ClassOrInterfaceDeclaration controller =
            web.cu().findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

    private Optional<String> methodPath(String method) {
        Expression value = Annotations.attribute(
                controller.getMethodsByName(method).get(0).getAnnotation(0), "value").orElseThrow();
        return resolver.resolveString(value, web);
    }

    @Test
    void resolvesConstantsAcrossClassesWithConcatenation() {
        Expression classPath = Annotations.attribute(controller.getAnnotation(0), "value").orElseThrow();

        assertThat(resolver.resolveString(classPath, web)).contains("/api/orders");
        assertThat(methodPath("a")).contains("/local/{id}");
        assertThat(methodPath("b")).contains("/api");
    }

    @Test
    void returnsEmptyForCyclesAndUnknownConstants() {
        assertThat(methodPath("c")).isEmpty();
        assertThat(methodPath("d")).isEmpty();
    }
}
