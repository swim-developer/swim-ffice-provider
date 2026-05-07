package com.github.swim_developer.unit;

import aero.fixm.ffice.FficeMessageType;
import com.github.swim_developer.ffice.provider.domain.model.FilterableEvent;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.infrastructure.in.amqp.IngressMessageHandler;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.ProviderEventStore;
import com.github.swim_developer.ffice.provider.infrastructure.out.xml.EventExtractor;
import com.github.swim_developer.ffice.provider.infrastructure.out.xml.JaxbUnmarshallerPool;
import com.github.swim_developer.framework.domain.exception.XmlValidationException;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, TestNameLoggerExtension.class})
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class FficeEventProcessorTest {

    private IngressMessageHandler processor;

    @Mock
    private ProviderEventStore eventRepository;

    @Mock
    private JaxbUnmarshallerPool jaxbPool;

    @Mock
    private EventExtractor eventExtractor;

    @Mock
    private HandoffCache handoffCache;

    @Mock
    private Vertx vertx;

    @Mock
    private TransactionSynchronizationRegistry txSyncRegistry;

    @Spy
    private MeterRegistry registry = new SimpleMeterRegistry();

    private final FficeMessageType stubParsed = new FficeMessageType();

    @BeforeEach
    void setUp() throws XmlValidationException {
        processor = new IngressMessageHandler(
                eventRepository, eventExtractor, jaxbPool,
                handoffCache, vertx, registry, txSyncRegistry);

        lenient().when(jaxbPool.unmarshalAndValidate(anyString())).thenReturn(stubParsed);
    }

    @Test
    void rejectsMessageWithJaxbValidationFailure() throws XmlValidationException {
        when(jaxbPool.unmarshalAndValidate(anyString()))
                .thenThrow(new XmlValidationException("Invalid FIXM message"));

        processor.processEvent("<bad/>");

        assertThat(registry.counter("ffice_events_failed_total", "reason", "jaxb_validation_failed").count())
                .isEqualTo(1.0);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsMessageWhenExtractionProducesEmpty() {
        when(eventExtractor.extract(any())).thenReturn(List.of(Optional.empty()));

        processor.processEvent("<ffice/>");

        assertThat(registry.counter("ffice_events_failed_total", "reason", "extraction_failed").count())
                .isEqualTo(1.0);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void persistsNewEventAndDispatchesForDelivery() {
        FilterableEvent event = new FilterableEvent("MSG-001", "GUFI-ABCD1234",
                "FILED_FLIGHT_PLAN", "<FficeMessage/>");
        when(eventExtractor.extract(any())).thenReturn(List.of(Optional.of(event)));
        when(eventRepository.findDomainById("MSG-001")).thenReturn(null);

        processor.processEvent("<FficeMessage/>");

        assertThat(registry.counter("ffice_events_persisted_total").count()).isEqualTo(1.0);
        assertThat(registry.counter("ffice_events_received_total", "type", "FILED_FLIGHT_PLAN").count())
                .isEqualTo(1.0);
        verify(txSyncRegistry).registerInterposedSynchronization(any());
    }

    @Test
    void updatesExistingEventOnDuplicate() {
        FilterableEvent event = new FilterableEvent("MSG-002", "GUFI-EFGH5678",
                "FLIGHT_ARRIVAL", "<FficeMessage/>");
        StoredEvent existing = StoredEvent.builder().eventId("MSG-002").build();
        when(eventExtractor.extract(any())).thenReturn(List.of(Optional.of(event)));
        when(eventRepository.findDomainById("MSG-002")).thenReturn(existing);

        processor.processEvent("<FficeMessage/>");

        verify(eventRepository, never()).persist((StoredEvent) any());
        assertThat(existing.getGufi()).isEqualTo("GUFI-EFGH5678");
        assertThat(existing.getMessageType()).isEqualTo("FLIGHT_ARRIVAL");
    }

    @Test
    void dispatchesAfterCommitSynchronizationOnPersist() {
        FilterableEvent event = new FilterableEvent("MSG-003", "GUFI-IJKL9012",
                "FLIGHT_DEPARTURE", "<FficeMessage/>");
        when(eventExtractor.extract(any())).thenReturn(List.of(Optional.of(event)));
        when(eventRepository.findDomainById("MSG-003")).thenReturn(null);

        processor.processEvent("<FficeMessage/>");

        verify(txSyncRegistry).registerInterposedSynchronization(any());
    }

    @Test
    void handlesDbPersistenceFailureWithoutPropagating() {
        FilterableEvent event = new FilterableEvent("MSG-004", "GUFI-MNOP3456",
                "PLANNING_STATUS", "<FficeMessage/>");
        when(eventExtractor.extract(any())).thenReturn(List.of(Optional.of(event)));
        when(eventRepository.findDomainById("MSG-004")).thenThrow(new RuntimeException("DB unavailable"));

        processor.processEvent("<FficeMessage/>");

        assertThat(registry.counter("ffice_events_failed_total", "reason", "persistence_failed").count())
                .isEqualTo(1.0);
        verifyNoInteractions(txSyncRegistry);
    }

    @Test
    void tracksReceivedCounterByMessageType() {
        String[] messageTypes = {
                "FILED_FLIGHT_PLAN", "FLIGHT_ARRIVAL", "FLIGHT_DEPARTURE",
                "FLIGHT_CANCELLATION", "FLIGHT_PLAN_UPDATE", "FILING_STATUS", "PLANNING_STATUS"
        };

        for (String type : messageTypes) {
            FilterableEvent event = new FilterableEvent("MSG-" + type, "GUFI-X", type, "<msg/>");
            when(eventExtractor.extract(any())).thenReturn(List.of(Optional.of(event)));
            when(eventRepository.findDomainById("MSG-" + type)).thenReturn(null);
            processor.processEvent("<msg/>");

            assertThat(registry.counter("ffice_events_received_total", "type", type).count())
                    .as("Counter for type %s", type)
                    .isEqualTo(1.0);
        }
    }
}
