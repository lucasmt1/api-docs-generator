package dev.apidocs.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConstraintTest {

    @Test
    void describesAsAnnotationWithSortedAttributes() {
        Constraint size = new Constraint("Size", Map.of("min", "1", "max", "5"));

        assertThat(size.describe()).isEqualTo("@Size(max=5, min=1)");
        assertThat(Constraint.of("Email").describe()).isEqualTo("@Email");
        assertThat(size.attribute("min")).contains("1");
        assertThat(size.attribute("value")).isEmpty();
    }
}
