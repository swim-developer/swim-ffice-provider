package com.github.swim_developer.ffice.provider.infrastructure.out.metrics;

import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.provider.application.metrics.AbstractSubscriptionMetrics;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.JpaSubscriptionStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class SubscriptionMetrics extends AbstractSubscriptionMetrics {

    private final JpaSubscriptionStore subscriptionRepository;
    private final LeaderElection leaderElection;

    @Inject
    protected SubscriptionMetrics(MeterRegistry registry,
                                  JpaSubscriptionStore subscriptionRepository,
                                  LeaderElection leaderElection) {
        super(registry);
        this.subscriptionRepository = subscriptionRepository;
        this.leaderElection = leaderElection;
    }

    protected SubscriptionMetrics() {
        this(null, null, null);
    }

    @Override
    protected String getServiceName() {
        return "ffice";
    }

    @Override
    protected double countActiveSubscriptions() {
        try {
            return subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        } catch (Exception e) {
            log.warn("Failed to count active subscriptions", e);
            return 0;
        }
    }

    @Override
    protected void registerCustomGauges() {
        // TODO: Register domain-specific gauges (e.g., by scenario, by airport)
    }

    @Override
    protected void performGaugeUpdate() {
        // TODO: Update domain-specific gauges
    }

    void onStart(@Observes StartupEvent ev) {
        updateGauges();
    }

    @Scheduled(every = "30s")
    void scheduledUpdate() {
        if (!leaderElection.isLeader()) return;
        updateGauges();
    }
}
