package dev.apidocs.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ServiceMethod;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ContextBudgetTest {

    private static ServiceMethodRef method(String name, int bodyLength) {
        return new ServiceMethodRef("S", new ServiceMethod(name, name + "()", "x".repeat(bodyLength), false, false,
                List.of(), List.of(), ""));
    }

    private static final Function<List<ServiceMethodRef>, String> RENDER = methods -> methods.stream()
            .map(ref -> ref.method().body()).collect(Collectors.joining("\n"));

    @Test
    void keepsTextThatFits() {
        ContextBudget.Fitted fitted = new ContextBudget().fit(RENDER, List.of(method("a", 400)), 1_000);

        assertThat(fitted.truncated()).isFalse();
        assertThat(fitted.overBudget()).isFalse();
        assertThat(fitted.text()).hasSize(400);
    }

    @Test
    void halvesTheLongestBodiesFirst() {
        ContextBudget.Fitted fitted = new ContextBudget().fit(RENDER, List.of(method("big", 4_000), method("small", 400)), 700);

        assertThat(fitted.truncated()).isTrue();
        assertThat(fitted.overBudget()).isFalse();
        assertThat(fitted.text()).contains(ContextBudget.TRUNCATION_MARKER);
        assertThat(TokenEstimator.estimate(fitted.text())).isLessThanOrEqualTo(700);
        assertThat(fitted.text()).endsWith("x".repeat(400));
    }

    @Test
    void reportsWhenNothingElseCanBeCut() {
        ContextBudget.Fitted fitted = new ContextBudget().fit(RENDER, List.of(method("a", 150)), 10);

        assertThat(fitted.overBudget()).isTrue();
    }
}
