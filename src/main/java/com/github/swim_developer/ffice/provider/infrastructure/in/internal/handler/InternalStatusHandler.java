package com.github.swim_developer.ffice.provider.infrastructure.in.internal.handler;

import com.github.swim_developer.ffice.provider.infrastructure.in.internal.InternalResponseHelper;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.ProviderEventStore;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.JpaSubscriptionStore;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.infrastructure.out.cluster.LeaderElection;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InternalStatusHandler {

    private final Vertx vertx;
    private final JpaSubscriptionStore subscriptionRepository;
    private final ProviderEventStore eventRepository;
    private final LeaderElection leaderElection;

    @Inject
    public InternalStatusHandler(Vertx vertx,
                                 JpaSubscriptionStore subscriptionRepository,
                                 ProviderEventStore eventRepository,
                                 LeaderElection leaderElection) {
        this.vertx = vertx;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.leaderElection = leaderElection;
    }

    public void handleStatus(RoutingContext ctx) {
        io.vertx.core.Vertx core = vertx.getDelegate();
        core.getOrCreateContext().executeBlocking(() -> {
            long active = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
            long received = eventRepository.countByStatus(EventStatus.RECEIVED);
            long delivered = eventRepository.countByStatus(EventStatus.DELIVERED);
            return new JsonObject()
                    .put("status", "UP")
                    .put("leader", leaderElection.isLeader())
                    .put("subscriptions", new JsonObject().put("active", active))
                    .put("events", new JsonObject()
                            .put("received", received)
                            .put("delivered", delivered));
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                InternalResponseHelper.sendJson(ctx, 200, ar.result());
            } else {
                InternalResponseHelper.sendJson(ctx, 503, new JsonObject()
                        .put("status", "DOWN").put("error", ar.cause().getMessage()));
            }
        });
    }
}
