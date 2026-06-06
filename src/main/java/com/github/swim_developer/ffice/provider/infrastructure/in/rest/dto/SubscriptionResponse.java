package com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RegisterForReflection
public record SubscriptionResponse(
        String topic,
        @JsonProperty("subscription_id") UUID subscriptionId,
        String queue,
        @JsonProperty("subscription_status") SubscriptionStatus subscriptionStatus,
        QualityOfService qos,
        Boolean durable,
        @JsonProperty("subscription_end") Instant subscriptionEnd,
        @JsonProperty("provider_name") String providerName,
        @JsonProperty("heartbeat_queue") String heartbeatQueue,
        @JsonProperty("message_type") List<String> messageType,
        String description,
        String comment
) {
}
