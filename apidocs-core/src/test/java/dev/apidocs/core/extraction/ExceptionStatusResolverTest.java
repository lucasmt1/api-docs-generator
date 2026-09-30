package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionStatusResolverTest {

    private final ExceptionStatusResolver resolver = new ExceptionStatusResolver(
            JavaSnippets.index(
                    "package com.x; public abstract class BusinessException extends RuntimeException {}",
                    "package com.x; public class StockException extends BusinessException {}",
                    "package com.x; public class StateException extends BusinessException {}"),
            List.of(new ExceptionMapping("BusinessException", 409), new ExceptionMapping("StateException", 422)));

    @Test
    void usesTheMostSpecificMappingWalkingSuperclasses() {
        assertThat(resolver.statusOf("StockException")).hasValue(409);
        assertThat(resolver.statusOf("StateException")).hasValue(422);
        assertThat(resolver.statusOf("RuntimeException")).isEmpty();
        assertThat(resolver.statusOf("Unknown")).isEmpty();
    }

    @Test
    void knowsWhichExceptionsBelongToTheProject() {
        assertThat(resolver.isProjectType("StockException")).isTrue();
        assertThat(resolver.isProjectType("IllegalStateException")).isFalse();
    }
}
