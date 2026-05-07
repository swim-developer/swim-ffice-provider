package com.github.swim_developer.ffice.provider.infrastructure.in.rest.mapper;

import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionResult;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionRequest;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionResponse;
import jakarta.enterprise.context.ApplicationScoped;

// TODO: Update mapping when you add domain-specific fields
@ApplicationScoped
public class ProviderSubscriptionMapper {

    public SubscriptionCommand toCommand(SubscriptionRequest request) {
        return new SubscriptionCommand(
                request.topic(),
                request.qos(),
                request.durable(),
                request.queueName(),
                request.messageType(),
                request.description(),
                request.comment()
        );
    }

    public SubscriptionResponse toResponse(SubscriptionResult result) {
        return new SubscriptionResponse(
                result.topic(),
                result.subscriptionId(),
                result.queue(),
                result.subscriptionStatus(),
                result.qos(),
                result.durable(),
                result.subscriptionEnd(),
                result.providerName(),
                result.heartbeatQueue(),
                result.messageType(),
                result.description(),
                result.comment()
        );
    }
}
