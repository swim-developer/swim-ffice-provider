package com.github.swim_developer.ffice.provider.infrastructure.out.persistence;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.domain.model.FailedDelivery;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity.EventJpaEntity;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity.FailedDeliveryJpaEntity;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity.SubscriptionJpaEntity;
import jakarta.enterprise.context.ApplicationScoped;

// TODO: Update mapping when you add domain-specific fields to entities
@ApplicationScoped
public class ProviderPersistenceMapper {

    public SubscriptionJpaEntity toJpa(Subscription domain) {
        return SubscriptionJpaEntity.builder()
                .subscriptionId(domain.getSubscriptionId())
                .topic(domain.getTopic()).queue(domain.getQueue())
                .messageType(domain.getMessageType())
                .status(domain.getStatus()).qos(domain.getQos())
                .durable(domain.getDurable()).userId(domain.getUserId())
                .subscriptionHash(domain.getSubscriptionHash())
                .createdAt(domain.getCreatedAt()).updatedAt(domain.getUpdatedAt())
                .subscriptionEnd(domain.getSubscriptionEnd())
                .description(domain.getDescription()).comment(domain.getComment())
                .build();
    }

    public Subscription toDomain(SubscriptionJpaEntity jpa) {
        return Subscription.builder()
                .subscriptionId(jpa.getSubscriptionId())
                .topic(jpa.getTopic()).queue(jpa.getQueue())
                .messageType(jpa.getMessageType())
                .status(jpa.getStatus()).qos(jpa.getQos())
                .durable(jpa.getDurable()).userId(jpa.getUserId())
                .subscriptionHash(jpa.getSubscriptionHash())
                .createdAt(jpa.getCreatedAt()).updatedAt(jpa.getUpdatedAt())
                .subscriptionEnd(jpa.getSubscriptionEnd())
                .description(jpa.getDescription()).comment(jpa.getComment())
                .build();
    }

    public EventJpaEntity toJpa(StoredEvent domain) {
        return EventJpaEntity.builder()
                .eventId(domain.getEventId()).gufi(domain.getGufi()).messageType(domain.getMessageType())
                .status(domain.getStatus()).receivedAt(domain.getReceivedAt()).processedAt(domain.getProcessedAt())
                .deliveredCount(domain.getDeliveredCount()).retryCount(domain.getRetryCount())
                .xmlMessage(domain.getXmlMessage()).build();
    }

    public StoredEvent toDomain(EventJpaEntity jpa) {
        return StoredEvent.builder()
                .eventId(jpa.getEventId()).gufi(jpa.getGufi()).messageType(jpa.getMessageType())
                .status(jpa.getStatus()).receivedAt(jpa.getReceivedAt()).processedAt(jpa.getProcessedAt())
                .deliveredCount(jpa.getDeliveredCount()).retryCount(jpa.getRetryCount())
                .xmlMessage(jpa.getXmlMessage()).build();
    }

    public FailedDeliveryJpaEntity toJpa(FailedDelivery domain) {
        return FailedDeliveryJpaEntity.builder()
                .id(domain.getId()).eventId(domain.getEventId())
                .subscriptionId(domain.getSubscriptionId()).queue(domain.getQueue())
                .errorMessage(domain.getErrorMessage()).retryCount(domain.getRetryCount())
                .resolved(domain.isResolved()).createdAt(domain.getCreatedAt())
                .resolvedAt(domain.getResolvedAt()).build();
    }

    public FailedDelivery toDomain(FailedDeliveryJpaEntity jpa) {
        return FailedDelivery.builder()
                .id(jpa.getId()).eventId(jpa.getEventId())
                .subscriptionId(jpa.getSubscriptionId()).queue(jpa.getQueue())
                .errorMessage(jpa.getErrorMessage()).retryCount(jpa.getRetryCount())
                .resolved(jpa.isResolved()).createdAt(jpa.getCreatedAt())
                .resolvedAt(jpa.getResolvedAt()).build();
    }
}
