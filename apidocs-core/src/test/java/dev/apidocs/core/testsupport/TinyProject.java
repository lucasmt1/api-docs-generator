package dev.apidocs.core.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Writes a minimal Spring Boot project (1 controller, 1 service, 1 DTO) for pipeline tests. */
public final class TinyProject {

    public static final String CONTROLLER_NARRATIVE = """
            {"controllerSummary": "Greets people.",
             "endpoints": [{"endpointId": "HelloController#greet", "title": "Greet", "summary": "Greets someone.",
               "description": "Builds a greeting.", "businessRules": ["The name must not be blank"],
               "errorScenarios": [], "requestExample": "", "responseExample": "{\\"message\\": \\"Hello Ana\\"}"}]}
            """;
    public static final String TECHNICAL_NARRATIVE = """
            {"overview": "A greeting service.", "domainConcepts": [], "businessRules": [], "errorHandling": "",
             "glossary": []}
            """;
    public static final String ARCHITECTURE_NARRATIVE = """
            {"summary": "Two layers.", "layers": [], "patterns": [], "alertComments": [], "recommendations": []}
            """;

    private TinyProject() {
    }

    public static Path write(Path dir) {
        Map<String, String> files = Map.of(
                "pom.xml", """
                        <project>
                          <parent><artifactId>spring-boot-starter-parent</artifactId><version>4.1.1</version></parent>
                          <artifactId>tiny</artifactId>
                          <name>Tiny API</name>
                        </project>
                        """,
                "src/main/java/com/tiny/HelloController.java", """
                        package com.tiny;
                        @RestController
                        @RequestMapping("/hello")
                        public class HelloController {
                            private final GreetingService service;
                            public HelloController(GreetingService service) { this.service = service; }
                            @GetMapping("/{name}")
                            public Greeting greet(@PathVariable String name) { return service.greet(name); }
                        }
                        """,
                "src/main/java/com/tiny/GreetingService.java", """
                        package com.tiny;
                        @Service
                        public class GreetingService {
                            public Greeting greet(String name) {
                                if (name.isBlank()) throw new IllegalArgumentException("name");
                                return new Greeting("Hello " + name);
                            }
                        }
                        """,
                "src/main/java/com/tiny/Greeting.java", "package com.tiny;\npublic record Greeting(String message) {}\n");
        try {
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path path = dir.resolve(file.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, file.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }
}
