package com.github.swim_developer.ffice.provider.infrastructure.in.amqp;

import aero.fixm.ffice.FficeMessageType;
import com.github.swim_developer.ffice.provider.domain.model.FilterableEvent;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.infrastructure.out.messaging.OutboxEventProcessor;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.ProviderEventStore;
import com.github.swim_developer.ffice.provider.infrastructure.out.xml.EventExtractor;
import com.github.swim_developer.framework.application.port.in.SwimIngressHandler;
import com.github.swim_developer.framework.application.port.out.SwimXmlUnmarshallerPort;
import com.github.swim_developer.framework.domain.exception.XmlValidationException;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.provider.application.messaging.AfterCommitEventDispatcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class IngressMessageHandler implements SwimIngressHandler {

    private static final String FAILED_STATUS = "failed";

    private final ProviderEventStore eventRepository;
    private final EventExtractor eventExtractor;
    private final SwimXmlUnmarshallerPort<FficeMessageType> jaxbPool;
    private final HandoffCache handoffCache;
    private final Vertx vertx;
    private final MeterRegistry registry;
    private final TransactionSynchronizationRegistry txSyncRegistry;

    @Inject
    public IngressMessageHandler(ProviderEventStore eventRepository,
                                 EventExtractor eventExtractor,
                                 SwimXmlUnmarshallerPort<FficeMessageType> jaxbPool,
                                 HandoffCache handoffCache,
                                 Vertx vertx,
                                 MeterRegistry registry,
                                 TransactionSynchronizationRegistry txSyncRegistry) {
        this.eventRepository = eventRepository;
        this.eventExtractor = eventExtractor;
        this.jaxbPool = jaxbPool;
        this.handoffCache = handoffCache;
        this.vertx = vertx;
        this.registry = registry;
        this.txSyncRegistry = txSyncRegistry;
    }

    @Override
    @Transactional
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 30000)
    @Bulkhead(value = 100)
    @WithSpan("ffice.provider.process")
    public void processEvent(String xmlMessage) {
        Timer.Sample timerSample = Timer.start(registry);

        FficeMessageType parsed;
        try {
            parsed = jaxbPool.unmarshalAndValidate(xmlMessage);
        } catch (XmlValidationException e) {
            Span.current().setAttribute("ffice.validation", FAILED_STATUS);
            log.warn("FF-ICE JAXB validation failed — event rejected: {}", e.getMessage());
            incrementFailedCounter("jaxb_validation_failed");
            return;
        }

        Optional<FilterableEvent> extracted = eventExtractor.extract(parsed).stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        if (extracted.isEmpty()) {
            Span.current().setAttribute("ffice.extraction", FAILED_STATUS);
            log.warn("Failed to extract FF-ICE event from message");
            incrementFailedCounter("extraction_failed");
            return;
        }

        FilterableEvent event = extracted.get();
        String messageId = event.messageId();
        String gufi = event.gufi();
        String messageType = event.messageType();

        incrementReceivedCounter(messageType);

        Span.current().setAttribute("ffice.messageId", messageId);
        Span.current().setAttribute("ffice.gufi", gufi);
        Span.current().setAttribute("ffice.messageType", messageType);

        StoredEvent entity = persistWithStatusReceived(messageId, gufi, messageType, xmlMessage);
        if (entity == null) {
            Span.current().setAttribute("ffice.persist", FAILED_STATUS);
            return;
        }

        Span.current().setAttribute("ffice.persist", "success");
        dispatchForAsyncDelivery(entity);

        timerSample.stop(Timer.builder("ffice_event_processing_duration")
                .description("Time to process and persist an FF-ICE event")
                .tag("type", messageType)
                .register(registry));

        log.info("Event persisted and dispatched - MessageId: {}, GUFI: {}, Type: {}", messageId, gufi, messageType);
    }

    private StoredEvent persistWithStatusReceived(String messageId, String gufi, String messageType, String xml) {
        try {
            StoredEvent existing = eventRepository.findDomainById(messageId);
            if (existing != null) {
                existing.setGufi(gufi);
                existing.setMessageType(messageType);
                existing.setXmlMessage(xml);
                existing.setStatus(EventStatus.RECEIVED);
                existing.setDeliveredCount(0);
                existing.setRetryCount(0);
                existing.setProcessedAt(null);
                eventRepository.update(existing);
                incrementPersistedCounter();
                return existing;
            }

            StoredEvent entity = StoredEvent.builder()
                    .eventId(messageId)
                    .gufi(gufi)
                    .messageType(messageType)
                    .xmlMessage(xml)
                    .status(EventStatus.RECEIVED)
                    .build();
            eventRepository.persist(entity);
            incrementPersistedCounter();
            return entity;
        } catch (Exception e) {
            log.error("Failed to persist FF-ICE event: {}", messageId, e);
            incrementFailedCounter("persistence_failed");
            return null;
        }
    }

    private void dispatchForAsyncDelivery(StoredEvent entity) {
        txSyncRegistry.registerInterposedSynchronization(
                new AfterCommitEventDispatcher(entity.getEventId(), entity, handoffCache, vertx,
                        OutboxEventProcessor.OUTBOX_EVENT_ADDRESS));
    }

    private void incrementReceivedCounter(String messageType) {
        Counter.builder("ffice_events_received_total")
                .description("Total FF-ICE events received from Kafka")
                .tag("type", messageType)
                .register(registry)
                .increment();
    }

    private void incrementPersistedCounter() {
        Counter.builder("ffice_events_persisted_total")
                .description("Total FF-ICE events persisted to database with RECEIVED status")
                .register(registry)
                .increment();
    }

    private void incrementFailedCounter(String reason) {
        Counter.builder("ffice_events_failed_total")
                .description("Total FF-ICE events that failed processing")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
