package com.github.swim_developer.ffice.provider.infrastructure.in.internal;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public final class InternalResponseHelper {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private InternalResponseHelper() {
    }

    public static void sendSuccess(RoutingContext ctx, int status, String message) {
        ctx.response().setStatusCode(status).putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put("status", "accepted").put("message", message)
                        .put("timestamp", Instant.now().toString()).encode());
    }

    public static void sendError(RoutingContext ctx, int status, String error) {
        ctx.response().setStatusCode(status).putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(new JsonObject().put("error", error)
                        .put("timestamp", Instant.now().toString()).encode());
    }

    public static void sendJson(RoutingContext ctx, int status, JsonObject body) {
        body.put("timestamp", Instant.now().toString());
        ctx.response().setStatusCode(status).putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(body.encode());
    }
}
