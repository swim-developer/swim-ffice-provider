package com.github.swim_developer.ffice.provider.application.port.out;

import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.application.port.out.SwimSubscriptionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStore extends SwimSubscriptionRepository<Subscription> {

    Optional<Subscription> findSubscriptionById(UUID id);

    Optional<Subscription> findBySubscriptionHash(String hash);

    List<Subscription> findByStatus(SubscriptionStatus status);

    List<Subscription> findActiveSubscriptions();

    boolean existsByQueue(String queue);

    boolean existsBySubscriptionHash(String subscriptionHash);

    long countByStatus(SubscriptionStatus status);

    List<Subscription> findBySubscriptionEndBefore(Instant threshold);

    List<Subscription> findByStatusAndUpdatedAtBefore(SubscriptionStatus status, Instant threshold);
}
