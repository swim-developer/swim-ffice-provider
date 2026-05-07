package com.github.swim_developer.unit;

import aero.fixm.ffice.FficeMessageType;
import aero.fixm.ffice.validation.FficeUnmarshallerPool;
import com.github.swim_developer.ffice.provider.domain.model.FilterableEvent;
import com.github.swim_developer.ffice.provider.infrastructure.out.xml.EventExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EventExtractorTest {

    private static FficeUnmarshallerPool pool;
    private EventExtractor extractor;

    @BeforeAll
    static void initPool() {
        pool = new FficeUnmarshallerPool();
    }

    @BeforeEach
    void setUp() {
        extractor = new EventExtractor();
    }

    private FilterableEvent extractFirst(String xml) throws Exception {
        FficeMessageType parsed = (FficeMessageType) pool.unmarshalAndValidate(xml);
        List<Optional<FilterableEvent>> results = extractor.extract(parsed);
        assertThat(results).isNotEmpty();
        assertThat(results.getFirst()).isPresent();
        return results.getFirst().get();
    }

    private String loadXml(String filename) throws Exception {
        return Files.readString(Paths.get(Objects.requireNonNull(
            getClass().getClassLoader().getResource("events/" + filename)).toURI()));
    }

    @Test
    void extractsMessageTypeFromFiledFlightPlan() throws Exception {
        FilterableEvent event = extractFirst(loadXml("filed-flight-plan.xml"));
        assertThat(event.messageType()).isEqualTo("FILED_FLIGHT_PLAN");
    }

    @Test
    void extractsGufiFromFiledFlightPlan() throws Exception {
        FilterableEvent event = extractFirst(loadXml("filed-flight-plan.xml"));
        assertThat(event.gufi()).isEqualTo("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    }

    @Test
    void extractsMessageTypeFromFlightPlanUpdate() throws Exception {
        FilterableEvent event = extractFirst(loadXml("flight-plan-update.xml"));
        assertThat(event.messageType()).isEqualTo("FLIGHT_PLAN_UPDATE");
    }

    @Test
    void extractsMessageTypeFromFlightDeparture() throws Exception {
        FilterableEvent event = extractFirst(loadXml("flight-departure.xml"));
        assertThat(event.messageType()).isEqualTo("FLIGHT_DEPARTURE");
    }

    @Test
    void extractsMessageTypeFromFlightArrival() throws Exception {
        FilterableEvent event = extractFirst(loadXml("flight-arrival.xml"));
        assertThat(event.messageType()).isEqualTo("FLIGHT_ARRIVAL");
    }

    @Test
    void returnsEmptyOptionalForNullMessage() {
        List<Optional<FilterableEvent>> results = extractor.extract(null);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEmpty();
    }
}
