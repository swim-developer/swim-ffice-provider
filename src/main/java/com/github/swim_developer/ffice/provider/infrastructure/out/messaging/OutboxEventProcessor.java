package com.github.swim_developer.ffice.provider.infrastructure.out.messaging;

import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.infrastructure.out.cache.HandoffCache;
import com.github.swim_developer.framework.provider.application.messaging.AbstractOutboxEventProcessor;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.ProviderEventStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.time.temporal.ChronoUnit;
import com.github.swim_developer.ffice.provider.application.port.in.DeliverEventPort;

@ApplicationScoped
@Slf4j
public class OutboxEventProcessor extends AbstractOutboxEventProcessor<StoredEvent> {

    public static final String OUTBOX_EVENT_ADDRESS = "outbox.deliver";

    private final ProviderEventStore eventRepository;
    private final DeliverEventPort deliveryService;

    @Inject
    protected OutboxEventProcessor(HandoffCache handoffCache,
                                   MeterRegistry registry,
                                   ProviderEventStore eventRepository,
                                   DeliverEventPort deliveryService) {
        super(handoffCache, registry);
        this.eventRepository = eventRepository;
        this.deliveryService = deliveryService;
    }

    protected OutboxEventProcessor() {
        this(null, null, null, null);
    }

    @ConsumeEvent(OUTBOX_EVENT_ADDRESS)
    @Blocking
    @Timeout(value = 30, unit = ChronoUnit.SECONDS)
    @Bulkhead(250)
    @WithSpan("ffice.provider.outbox.deliver")
    public void onOutboxEvent(String eventId) {
        processWithMetrics(eventId);
    }

    @Override
    protected DeliveryResult deliver(StoredEvent entity) {
        return deliveryService.deliverToMatchingSubscriptions(entity);
    }

    @Override
    protected StoredEvent findEntityById(String eventId) {
        return eventRepository.findDomainById(eventId);
    }

    @Override
    protected StoredEvent mergeEntity(StoredEvent detached) {
        return eventRepository.mergeDomainEntity(detached);
    }

    @Override
    protected Class<StoredEvent> getEntityClass() {
        return StoredEvent.class;
    }

    @Override
    protected String getMetricPrefix() {
        return "ffice";
    }
}
