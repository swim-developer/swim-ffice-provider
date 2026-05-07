package com.github.swim_developer.ffice.provider.application.port.in;

import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionResult;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ManageSubscriptionPort {

    SubscriptionResult createSubscription(SubscriptionCommand command);

    SubscriptionResult getSubscription(UUID subscriptionId);

    List<SubscriptionResult> listSubscriptions();

    SubscriptionResult updateStatus(UUID subscriptionId, SubscriptionStatus newStatus);

    void deleteSubscription(UUID subscriptionId);

    SubscriptionResult renewSubscription(UUID subscriptionId, Duration extensionTtl);
}
