package com.github.swim_developer.ffice.provider.application.port.out;

import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;

public interface SubscriptionHashPort {

    String calculateHash(SubscriptionCommand command, String userId);
}
