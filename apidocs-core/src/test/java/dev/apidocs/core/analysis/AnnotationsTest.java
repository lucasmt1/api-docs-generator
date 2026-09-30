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
}
