package com.github.swim_developer.ffice.provider.infrastructure.out.subscription;

import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SubscriptionExpiry;
import com.github.swim_developer.framework.application.port.out.SubscriptionExpiryStrategy;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.JpaSubscriptionStore;
import com.github.swim_developer.ffice.provider.application.usecase.SubscriptionUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class ExpiryStrategyAdapter implements SubscriptionExpiryStrategy {

    private final JpaSubscriptionStore subscriptionRepository;
    private final SubscriptionUseCase subscriptionService;

    @Inject
    public ExpiryStrategyAdapter(JpaSubscriptionStore subscriptionRepository,
                                 SubscriptionUseCase subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public List<SubscriptionExpiry> findExpiredSubscriptions(Instant now) {
        return subscriptionRepository.findBySubscriptionEndBefore(now).stream()
                .map(this::toExpiry).toList();
    }

    @Override
    public List<SubscriptionExpiry> findTerminatedSubscriptionsToPurge(Instant threshold) {
        return subscriptionRepository.findByStatusAndUpdatedAtBefore(SubscriptionStatus.TERMINATED, threshold).stream()
                .map(this::toExpiry).toList();
    }

    @Override
    public void terminateSubscription(String subscriptionId) {
        subscriptionService.terminateSubscription(UUID.fromString(subscriptionId));
    }

    @Override
    public void purgeSubscription(String subscriptionId) {
        subscriptionService.purgeSubscription(UUID.fromString(subscriptionId));
    }

    private SubscriptionExpiry toExpiry(Subscription sub) {
        return new SubscriptionExpiry(sub.getSubscriptionId().toString(), sub.getSubscriptionEnd(), sub.getStatus().name());
    }
}
