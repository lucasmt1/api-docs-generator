package dev.apidocs.core.ai.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EndpointNarrativeTest {

    private static EndpointNarrative narrativeWith(ErrorScenario... scenarios) {
        return new EndpointNarrative("OrderController#create", "Create order", "Creates an order.", "", List.of(),
                List.of(scenarios), "", "");
    }

    @Test
    void whenForReturnsTheFirstNonBlankScenarioOfTheStatus() {
        EndpointNarrative narrative = narrativeWith(new ErrorScenario(409, "  "), new ErrorScenario(404, "Order missing"),
                new ErrorScenario(409, "No stock"), new ErrorScenario(409, "Other conflict"));

        assertThat(narrative.whenFor(409)).contains("No stock");
        assertThat(narrative.whenFor(404)).contains("Order missing");
    }

    @Test
    void whenForIsEmptyWhenNoUsableScenarioExistsForTheStatus() {
        EndpointNarrative narrative = narrativeWith(new ErrorScenario(409, ""), new ErrorScenario(404, "Order missing"));

        assertThat(narrative.whenFor(409)).isEmpty();
        assertThat(narrative.whenFor(500)).isEmpty();
    }
}
