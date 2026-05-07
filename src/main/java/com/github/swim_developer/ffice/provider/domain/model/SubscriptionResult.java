package com.github.swim_developer.ffice.provider.domain.model;

import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionResult(
        String topic,
        UUID subscriptionId,
        String queue,
        SubscriptionStatus subscriptionStatus,
        QualityOfService qos,
        Boolean durable,
        Instant subscriptionEnd,
        String providerName,
        String heartbeatQueue,
        List<String> messageType,
        String description,
        String comment
) {
}
