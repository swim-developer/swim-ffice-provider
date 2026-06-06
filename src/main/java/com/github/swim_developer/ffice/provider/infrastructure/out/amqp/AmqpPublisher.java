package com.github.swim_developer.ffice.provider.infrastructure.out.amqp;

import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.application.port.out.AmqpPublisherHealthProvider;
import com.github.swim_developer.framework.provider.infrastructure.out.amqp.AbstractAmqpPublisher;
import com.github.swim_developer.framework.provider.infrastructure.out.amqp.CircuitBreakerOpenException;
import com.github.swim_developer.framework.provider.infrastructure.out.amqp.PerQueueCircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AmqpPublisher extends AbstractAmqpPublisher implements AmqpPublisherHealthProvider {

    private final Emitter<String> amqpEmitter;

    @ConfigProperty(name = "swim.amqp.circuit-breaker.failure-threshold", defaultValue = "10")
    int failureThreshold;

    @ConfigProperty(name = "swim.amqp.circuit-breaker.open-duration-ms", defaultValue = "30000")
    long openDurationMs;

    @ConfigProperty(name = "swim.amqp.circuit-breaker.success-threshold", defaultValue = "3")
    int successThreshold;

    private PerQueueCircuitBreaker circuitBreaker;

    @Inject
    public AmqpPublisher(@Channel("ffice-amqp-out") Emitter<String> amqpEmitter) {
        this.amqpEmitter = amqpEmitter;
    }

    @PostConstruct
    void init() {
        circuitBreaker = new PerQueueCircuitBreaker(failureThreshold, openDurationMs, successThreshold);
    }

    @Override
    protected Emitter<String> getEmitter() {
        return amqpEmitter;
    }

    @Override
    protected Optional<PerQueueCircuitBreaker> getCircuitBreaker() {
        return Optional.ofNullable(circuitBreaker);
    }

    @Override
    @Retry(maxRetries = 3, delay = 1000, jitter = 500, abortOn = CircuitBreakerOpenException.class)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public void publishToQueue(String queue, String payload, QualityOfService qos, UUID subscriptionId) {
        super.publishToQueue(queue, payload, qos, subscriptionId);
    }
}
