package com.github.swim_developer.ffice.provider.infrastructure.out.subscription;

import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionHashPort;
import com.github.swim_developer.ffice.provider.domain.model.SubscriptionCommand;
import com.github.swim_developer.framework.application.service.AbstractSubscriptionHashCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

// TODO: Add domain-specific fields to hash calculation
@ApplicationScoped
@Slf4j
public class SubscriptionHashCalculatorAdapter extends AbstractSubscriptionHashCalculator<SubscriptionCommand>
        implements SubscriptionHashPort {

    @Override
    public String calculateHash(SubscriptionCommand command, String userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("userId:").append(userId).append("|");
        sb.append("topic:").append(command.topic()).append("|");
        sb.append("description:").append(nullSafe(command.description()));
        String data = sb.toString();
        log.debug("Calculating hash for: {}", data);
        return sha256(data);
    }
}
