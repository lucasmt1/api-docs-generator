package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.Warning;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionMappingExtractorTest {

    @Test
    void readsAdviceHandlersAndResponseStatusExceptions() {
        var index = JavaSnippets.index(
                """
                package com.x;
                @RestControllerAdvice
                public class Handler {
                    @ExceptionHandler(NotFoundException.class)
                    @ResponseStatus(HttpStatus.NOT_FOUND)
                    public Object a(NotFoundException e) { return null; }

                    @ExceptionHandler({ConflictException.class, DuplicateException.class})
                    public ResponseEntity<Object> b(RuntimeException e) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }

                    @ExceptionHandler
                    public ProblemDetail c(ValidationException e) {
                        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "x");
                    }

                    @ExceptionHandler(Mystery.class)
                    public Object d() { return null; }
                }
                """,
                "package com.x; @ResponseStatus(HttpStatus.GONE) public class GoneException extends RuntimeException {}");
        List<Warning> warnings = new ArrayList<>();

        List<ExceptionMapping> mappings = new ExceptionMappingExtractor(index).extract(warnings);

        assertThat(mappings).containsExactly(
                new ExceptionMapping("ConflictException", 409),
                new ExceptionMapping("DuplicateException", 409),
                new ExceptionMapping("GoneException", 410),
                new ExceptionMapping("NotFoundException", 404),
                new ExceptionMapping("ValidationException", 422));
        assertThat(warnings).extracting(Warning::code).containsExactly("HANDLER_STATUS_UNKNOWN");
    }
}
