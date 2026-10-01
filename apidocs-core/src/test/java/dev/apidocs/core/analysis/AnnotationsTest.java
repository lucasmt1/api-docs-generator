package dev.apidocs.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.Test;

class AnnotationsTest {

    private final ClassOrInterfaceDeclaration type = StaticJavaParser.parse("""
            @org.springframework.web.bind.annotation.RestController
            @RequestMapping(value = {"/a", "/b"}, produces = "application/json")
            class C {
                @Size(min = 1, max = 10L) @Min(-5) @ResponseStatus(HttpStatus.CREATED) void m() {}
            }
            """).findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
    private final MethodDeclaration method = type.getMethods().get(0);

    @Test
    void findsAnnotationsBySimpleOrQualifiedName() {
        assertThat(Annotations.has(type, "RestController")).isTrue();
        assertThat(Annotations.has(type, "Controller")).isFalse();
        assertThat(Annotations.find(type, "Controller", "RestController")).isPresent();
    }

    @Test
    void readsAttributesOfNormalAndSingleMemberAnnotations() {
        AnnotationExpr mapping = Annotations.find(type, "RequestMapping").orElseThrow();
        Expression values = Annotations.attribute(mapping, "value").orElseThrow();

        assertThat(Annotations.elements(values)).hasSize(2);
        assertThat(Annotations.firstAttribute(mapping, "path", "value")).isPresent();
        assertThat(Annotations.attribute(mapping, "produces").flatMap(Annotations::stringLiteral)).contains("application/json");

        AnnotationExpr status = Annotations.find(method, "ResponseStatus").orElseThrow();
        assertThat(Annotations.lastIdentifier(Annotations.attribute(status, "value").orElseThrow())).isEqualTo("CREATED");
        assertThat(Annotations.attribute(status, "code")).isEmpty();
    }

    @Test
    void rendersLiteralValuesAsPlainText() {
        AnnotationExpr size = Annotations.find(method, "Size").orElseThrow();
        AnnotationExpr min = Annotations.find(method, "Min").orElseThrow();

        assertThat(Annotations.valueText(Annotations.attribute(size, "max").orElseThrow())).isEqualTo("10");
        assertThat(Annotations.valueText(Annotations.attribute(min, "value").orElseThrow())).isEqualTo("-5");
        assertThat(Annotations.valueText(StaticJavaParser.parseExpression("\"a\\\\d\""))).isEqualTo("a\\d");
        assertThat(Annotations.lastIdentifier(StaticJavaParser.parseExpression("Foo.class"))).isEqualTo("Foo");
    }

    @Test
    void rendersIntegerLiteralsInDecimal() {
        assertThat(valueOf("10_000")).isEqualTo("10000");
        assertThat(valueOf("0x10")).isEqualTo("16");
        assertThat(valueOf("0b101")).isEqualTo("5");
        assertThat(valueOf("017")).isEqualTo("15");
        assertThat(valueOf("0")).isEqualTo("0");
        assertThat(valueOf("1_000_000L")).isEqualTo("1000000");
        assertThat(valueOf("0xFFFFFFFF")).isEqualTo("-1");
        assertThat(valueOf("-0x10")).isEqualTo("-16");
        assertThat(valueOf("-2147483648")).isEqualTo("-2147483648");
        assertThat(valueOf("-9223372036854775808L")).isEqualTo("-9223372036854775808");
        assertThat(valueOf("99999999999")).isEqualTo("99999999999");
    }

    private static String valueOf(String expression) {
        return Annotations.valueText(StaticJavaParser.parseExpression(expression));
    }
}
