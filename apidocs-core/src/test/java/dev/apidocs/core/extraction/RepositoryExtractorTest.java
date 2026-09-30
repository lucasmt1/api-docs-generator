package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.RepositoryInfo;
import dev.apidocs.core.testsupport.JavaSnippets;
import org.junit.jupiter.api.Test;

class RepositoryExtractorTest {

    @Test
    void findsSpringDataRepositoriesAndAnnotatedDaos() {
        var index = JavaSnippets.index(
                "package com.x; import org.springframework.data.jpa.repository.JpaRepository; "
                        + "public interface OrderRepository extends JpaRepository<Order, Long> {}",
                "package com.x; @Repository public class LegacyDao {}",
                "package com.x; public interface Other extends Comparable<String> {}");

        assertThat(new RepositoryExtractor(index).extract()).containsExactly(
                new RepositoryInfo("LegacyDao", "com.x.LegacyDao", "", ""),
                new RepositoryInfo("OrderRepository", "com.x.OrderRepository", "Order", "Long"));
    }
}
