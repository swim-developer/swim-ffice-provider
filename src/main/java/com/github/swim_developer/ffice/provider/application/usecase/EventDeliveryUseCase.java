package com.github.swim_developer.ffice.provider.application.usecase;

import com.github.swim_developer.ffice.provider.application.port.in.DeliverEventPort;
import com.github.swim_developer.ffice.provider.domain.model.FilterableEvent;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.provider.application.subscription.AbstractEventDeliveryService;
import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.domain.model.FailedDelivery;
import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionStore;
import com.github.swim_developer.framework.application.port.out.SwimAmqpPublisherPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class EventDeliveryUseCase extends AbstractEventDeliveryService<StoredEvent, FilterableEvent, Subscription>
        implements DeliverEventPort {

    private final SubscriptionStore subscriptionRepository;
    private final SwimAmqpPublisherPort amqpPublisher;
    private final FailedDeliveryStore<FailedDelivery> failedDeliveryRepository;
    private final MeterRegistry registry;

    @Inject
    public EventDeliveryUseCase(SubscriptionStore subscriptionRepository,
                                SwimAmqpPublisherPort amqpPublisher,
                                FailedDeliveryStore<FailedDelivery> failedDeliveryRepository,
                                MeterRegistry registry) {
        this.subscriptionRepository = subscriptionRepository;
        this.amqpPublisher = amqpPublisher;
        this.failedDeliveryRepository = failedDeliveryRepository;
        this.registry = registry;
    }

    @Override
    protected FilterableEvent toFilterableModel(StoredEvent entity) {
        return new FilterableEvent(entity.getEventId(), entity.getGufi(),
                entity.getMessageType(), entity.getXmlMessage());
    }

    @Override
    protected String extractPayload(StoredEvent entity) {
        return entity.getXmlMessage();
    }

    @Override
    protected String extractEventId(StoredEvent entity) {
        return entity.getEventId();
    }

    @Override
    protected List<Subscription> loadActiveSubscriptions() {
        return subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
    }

    @Override
    protected void publishToQueue(String queue, String payload, QualityOfService qos, UUID subscriptionId) {
        amqpPublisher.publishToQueue(queue, payload, qos, subscriptionId);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<FailedDeliveryStore<SwimFailedDelivery>> getFailedDeliveryStore() {
        return Optional.of((FailedDeliveryStore<SwimFailedDelivery>) (FailedDeliveryStore<?>) failedDeliveryRepository);
    }

    @Override
    protected void onDeliverySuccess(StoredEvent entity, Subscription subscription) {
        Counter.builder("ffice_events_delivered_total")
                .register(registry).increment();
    }

    @Override
    protected void onDeliveryFailure(StoredEvent entity, Subscription subscription, Exception e) {
        Counter.builder("ffice_events_delivery_failed_total")
                .register(registry).increment();
    }
}
