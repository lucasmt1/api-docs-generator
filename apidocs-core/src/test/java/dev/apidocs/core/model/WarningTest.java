package dev.apidocs.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarningTest {

    @Test
    void sortsByCodeThenLocationAndRemovesDuplicates() {
        Warning b = new Warning("B", "m", "x");
        Warning a2 = new Warning("A", "m", "y");
        Warning a1 = new Warning("A", "m", "x");

        assertThat(Warnings.sortedDistinct(List.of(b, a2, a1, a1))).containsExactly(a1, a2, b);
        assertThat(Warning.of("C", "msg").location()).isEmpty();
    }
}
