package com.github.swim_developer.ffice.provider.infrastructure.out.mapper;

import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionMappingPort;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionResult;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

// TODO: Update mapping when you add domain-specific subscription fields
@ApplicationScoped
@Slf4j
public class ProviderSubscriptionMappingAdapter implements SubscriptionMappingPort {

    private static final String QUEUE_PREFIX = "FFICE";

    @ConfigProperty(name = "swim.subscription.expiry.default-ttl", defaultValue = "24h")
    Duration defaultTtl;

    @ConfigProperty(name = "swim.provider.name", defaultValue = "SWIM-FF-ICE-Provider")
    String providerName;

    public Subscription toEntity(SubscriptionCommand command, String userId, String subscriptionHash, String resolvedQueueName) {
        UUID subscriptionId = UUID.randomUUID();
        String queue = resolvedQueueName != null ? resolvedQueueName :
                String.format("%s-%s-%s", QUEUE_PREFIX, userId, subscriptionId);
        QualityOfService qos = command.qos() != null ? command.qos() : QualityOfService.AT_LEAST_ONCE;
        Boolean durable = command.durable() != null ? command.durable() : Boolean.TRUE;
        Instant now = Instant.now();
        return Subscription.builder()
                .subscriptionId(subscriptionId)
                .topic(command.topic()).qos(qos).durable(durable)
                .queue(queue).userId(userId).subscriptionHash(subscriptionHash)
                .messageType(command.messageType() != null ? command.messageType() : new java.util.ArrayList<>())
                .description(command.description()).comment(command.comment())
                .createdAt(now).updatedAt(now).subscriptionEnd(now.plus(defaultTtl))
                .build();
    }

    public SubscriptionResult toResponse(Subscription sub) {
        return new SubscriptionResult(
                sub.getTopic(), sub.getSubscriptionId(), sub.getQueue(),
                sub.getStatus(), sub.getQos(), sub.getDurable(),
                sub.getSubscriptionEnd(), providerName,
                sub.getQueue() + "-heartbeat",
                sub.getMessageType(),
                sub.getDescription(), sub.getComment()
        );
    }
}
