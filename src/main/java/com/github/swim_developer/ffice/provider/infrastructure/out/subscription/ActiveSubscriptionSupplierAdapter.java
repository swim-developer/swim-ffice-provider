package com.github.swim_developer.ffice.provider.infrastructure.out.subscription;

import com.github.swim_developer.framework.domain.model.ActiveSubscriptionInfo;
import com.github.swim_developer.framework.application.port.out.ActiveSubscriptionSupplier;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.JpaSubscriptionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ActiveSubscriptionSupplierAdapter implements ActiveSubscriptionSupplier {

    private final JpaSubscriptionStore subscriptionRepository;

    @Inject
    public ActiveSubscriptionSupplierAdapter(JpaSubscriptionStore subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public List<ActiveSubscriptionInfo> getActiveSubscriptions() {
        return subscriptionRepository.findActiveSubscriptions().stream()
                .map(sub -> new ActiveSubscriptionInfo(sub.getSubscriptionId(), sub.getQueue(), sub.getStatus().name()))
                .toList();
    }
}
