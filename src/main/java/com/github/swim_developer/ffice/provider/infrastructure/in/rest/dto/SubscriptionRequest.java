package com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@RegisterForReflection
public record SubscriptionRequest(
        @NotNull @Size(max = 100) String topic,
        QualityOfService qos,
        Boolean durable,
        @JsonProperty("queue_name") @JsonAlias("queueName") @Size(max = 255) String queueName,
        @JsonProperty("message_type") @JsonAlias("messageType") List<String> messageType,
        @Size(max = 500) String description,
        @Size(max = 500) String comment
) {
}
