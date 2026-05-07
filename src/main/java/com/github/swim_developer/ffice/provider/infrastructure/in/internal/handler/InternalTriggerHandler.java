package com.github.swim_developer.ffice.provider.infrastructure.in.internal.handler;

import com.github.swim_developer.ffice.provider.infrastructure.in.amqp.IngressMessageHandler;
import com.github.swim_developer.ffice.provider.infrastructure.in.internal.InternalResponseHelper;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class InternalTriggerHandler {

    private final Vertx vertx;
    private final IngressMessageHandler eventProcessor;

    @Inject
    public InternalTriggerHandler(Vertx vertx, IngressMessageHandler eventProcessor) {
        this.vertx = vertx;
        this.eventProcessor = eventProcessor;
    }

    public void handle(RoutingContext ctx) {
        String xmlMessage = ctx.body().asString();
        if (xmlMessage == null || xmlMessage.isBlank()) {
            InternalResponseHelper.sendError(ctx, 400, "Empty or missing XML body");
            return;
        }
        io.vertx.core.Vertx core = vertx.getDelegate();
        core.getOrCreateContext().executeBlocking(() -> {
            eventProcessor.processEvent(xmlMessage);
            return null;
        }).onComplete(ar -> {
            if (ar.succeeded()) {
                InternalResponseHelper.sendSuccess(ctx, 202, "Event accepted for processing");
            } else {
                log.error("Error processing triggered event", ar.cause());
                InternalResponseHelper.sendError(ctx, 500, ar.cause().getMessage());
            }
        });
    }
}
