package com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;

@RegisterForReflection
public record SubscriptionStatusUpdate(
        @NotNull(message = "subscription_status is required")
        @JsonProperty("subscription_status")
        SubscriptionStatus subscriptionStatus
) {
}
