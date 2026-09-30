package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusHeuristicsTest {

    private final ClassOrInterfaceDeclaration declaration = JavaSnippets.parse("""
            package com.x;
            @ResponseStatus(code = HttpStatus.NOT_FOUND)
            class Holder {
                @ResponseStatus(HttpStatus.CREATED) void created() { }
                @ResponseStatus(value = HttpStatus.UNPROCESSABLE_CONTENT) void explicit() { }
                @ResponseStatus(code = 202) void literal() { }
                @ResponseStatus(reason = "gone") void noCode() { }
                @Deprecated void plain() { }
                ResponseEntity<String> entities() {
                    ResponseEntity.unprocessableContent().build();
                    ResponseEntity.unprocessableEntity().build();
                    ResponseEntity.status(HttpStatus.CONFLICT).build();
                    return other.ok();
                }
            }
            """).get(0).cu().getClassByName("Holder").orElseThrow();

    private MethodDeclaration method(String name) {
        return declaration.getMethodsByName(name).get(0);
    }

    @Test
    void readsResponseStatusFromValueOrCodeOnMethodsAndClasses() {
        assertThat(StatusHeuristics.responseStatusCode(method("created"))).hasValue(201);
        assertThat(StatusHeuristics.responseStatusCode(method("explicit"))).hasValue(422);
        assertThat(StatusHeuristics.responseStatusCode(method("literal"))).hasValue(202);
        assertThat(StatusHeuristics.responseStatusCode(declaration)).hasValue(404);
    }

    @Test
    void reportsNoStatusWhenMissingOrWithoutCode() {
        assertThat(StatusHeuristics.responseStatusCode(method("noCode"))).isEmpty();
        assertThat(StatusHeuristics.responseStatusCode(method("plain"))).isEmpty();
    }

    @Test
    void mapsBothUnprocessableShortcutsAndIgnoresOtherScopes() {
        assertThat(StatusHeuristics.responseEntityStatuses(method("entities").getBody().orElseThrow()))
                .isEqualTo(List.of(422, 422, 409));
    }
}
