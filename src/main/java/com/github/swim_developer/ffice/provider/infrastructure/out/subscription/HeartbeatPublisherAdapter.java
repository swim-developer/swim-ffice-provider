package com.github.swim_developer.ffice.provider.infrastructure.out.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionHeartbeat;
import com.github.swim_developer.framework.application.port.out.SubscriptionHeartbeatPublisher;
import com.github.swim_developer.ffice.provider.infrastructure.out.amqp.AmqpPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class HeartbeatPublisherAdapter implements SubscriptionHeartbeatPublisher {

    private final AmqpPublisher amqpPublisher;
    private final ObjectMapper objectMapper;

    @Inject
    public HeartbeatPublisherAdapter(AmqpPublisher amqpPublisher, ObjectMapper objectMapper) {
        this.amqpPublisher = amqpPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishHeartbeat(String queueName, SubscriptionHeartbeat payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            amqpPublisher.publish(queueName, json, QualityOfService.AT_MOST_ONCE, HEARTBEAT_CONTENT_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish heartbeat for queue: " + queueName, e);
        }
    }
}
