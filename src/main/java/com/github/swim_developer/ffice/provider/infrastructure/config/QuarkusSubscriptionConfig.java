package com.github.swim_developer.ffice.provider.infrastructure.config;

import com.github.swim_developer.ffice.provider.application.port.in.ProviderSubscriptionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class QuarkusSubscriptionConfig implements ProviderSubscriptionConfig {

    private final Duration defaultTtl;

    @Inject
    public QuarkusSubscriptionConfig(
            @ConfigProperty(name = "swim.subscription.expiry.default-ttl", defaultValue = "24h")
            Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    @Override
    public Duration defaultTtl() {
        return defaultTtl;
    }
}
