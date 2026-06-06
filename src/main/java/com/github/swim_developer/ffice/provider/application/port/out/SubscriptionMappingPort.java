package com.github.swim_developer.ffice.provider.application.port.out;

import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionResult;

public interface SubscriptionMappingPort {

    Subscription toEntity(SubscriptionCommand command, String userId, String subscriptionHash, String resolvedQueueName);

    SubscriptionResult toResponse(Subscription subscription);
}
