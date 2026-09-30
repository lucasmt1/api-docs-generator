package dev.apidocs.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiDocsVersionTest {

    @Test
    void exposesTheMavenProjectVersion() {
        assertThat(ApiDocsVersion.get()).matches("\\d+\\.\\d+\\.\\d+.*");
    }
}
