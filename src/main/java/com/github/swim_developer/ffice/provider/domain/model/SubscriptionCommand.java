package com.github.swim_developer.ffice.provider.domain.model;

import com.github.swim_developer.framework.domain.model.QualityOfService;

import java.util.List;

public record SubscriptionCommand(
        String topic,
        QualityOfService qos,
        Boolean durable,
        String queueName,
        List<String> messageType,
        String description,
        String comment
) {
}
