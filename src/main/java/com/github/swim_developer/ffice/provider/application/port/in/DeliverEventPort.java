package com.github.swim_developer.ffice.provider.application.port.in;

import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.framework.domain.model.DeliveryResult;

public interface DeliverEventPort {

    DeliveryResult deliverToMatchingSubscriptions(StoredEvent event);
}
