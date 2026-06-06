package com.github.swim_developer.ffice.provider.application.usecase;

import com.github.swim_developer.ffice.provider.application.port.in.ProviderSubscriptionConfig;
import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionHashPort;
import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionMappingPort;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionResult;
import com.github.swim_developer.ffice.provider.application.port.in.ManageSubscriptionPort;
import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionStore;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionQueuePort;
import com.github.swim_developer.framework.provider.application.subscription.AbstractProviderSubscriptionService;
import com.github.swim_developer.framework.provider.application.subscription.TopicService;
import com.github.swim_developer.framework.application.port.out.SwimSecurityContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@ApplicationScoped
@Slf4j
public class SubscriptionUseCase
        extends AbstractProviderSubscriptionService<Subscription, SubscriptionCommand, SubscriptionResult>
        implements ManageSubscriptionPort {

    private static final String QUEUE_PREFIX = "FFICE-";

    private final SubscriptionMappingPort subscriptionMapper;
    private final TopicService topicService;
    private final SubscriptionHashPort hashCalculator;
    private final ProviderSubscriptionConfig config;

    @Inject
    protected SubscriptionUseCase(SwimSecurityContext securityContext,
                                  SwimSubscriptionQueuePort queueOrchestrator,
                                  SubscriptionStore subscriptionRepository,
                                  SubscriptionMappingPort subscriptionMapper,
                                  TopicService topicService,
                                  SubscriptionHashPort hashCalculator,
                                  ProviderSubscriptionConfig config) {
        super(securityContext, queueOrchestrator, subscriptionRepository);
        this.subscriptionMapper = subscriptionMapper;
        this.topicService = topicService;
        this.hashCalculator = hashCalculator;
        this.config = config;
    }

    protected SubscriptionUseCase() {
        this(null, null, null, null, null, null, null);
    }

    @Override
    protected String getQueuePrefix() {
        return QUEUE_PREFIX;
    }

    @Override
    protected Duration getDefaultTtl() {
        return config.defaultTtl();
    }

    @Override
    protected String getRequestedQueueName(SubscriptionCommand command) {
        return command.queueName();
    }

    @Override
    protected String calculateHash(SubscriptionCommand command, String userId) {
        return hashCalculator.calculateHash(command, userId);
    }

    @Override
    protected Subscription createEntity(SubscriptionCommand command, String userId, String queueName, String hash) {
        return subscriptionMapper.toEntity(command, userId, hash, queueName);
    }

    @Override
    protected SubscriptionResult mapToResponse(Subscription entity) {
        return subscriptionMapper.toResponse(entity);
    }

    @Override
    protected void validateRequest(SubscriptionCommand command, String userId) {
        try {
            topicService.getTopic(command.topic());
        } catch (NotFoundException e) {
            throw new BadRequestException("Topic not available: " + command.topic());
        }
    }
}
