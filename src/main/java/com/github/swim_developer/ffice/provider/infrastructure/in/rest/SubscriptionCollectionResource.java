package com.github.swim_developer.ffice.provider.infrastructure.in.rest;

import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionRequest;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.mapper.ProviderSubscriptionMapper;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionResponse;
import com.github.swim_developer.ffice.provider.application.port.in.ManageSubscriptionPort;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/swim/v1/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "mTLS")
@Tag(name = "Subscriptions", description = "Subscription lifecycle management")
@Slf4j
public class SubscriptionCollectionResource {

    private final ManageSubscriptionPort subscriptionService;
    private final ProviderSubscriptionMapper mapper;
    private final JsonWebToken jwt;

    @Inject
    public SubscriptionCollectionResource(ManageSubscriptionPort subscriptionService,
                                          ProviderSubscriptionMapper mapper,
                                          JsonWebToken jwt) {
        this.subscriptionService = subscriptionService;
        this.mapper = mapper;
        this.jwt = jwt;
    }

    @POST
    @Operation(operationId = "subscribe", summary = "Create a new subscription")
    @APIResponse(responseCode = "201", description = "Subscription created",
            content = @Content(schema = @Schema(implementation = SubscriptionResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request")
    @APIResponse(responseCode = "401", description = "Authentication failed")
    public Response subscribe(@Valid @RequestBody(required = true) SubscriptionRequest request) {
        log.info("Subscription CREATE for topic: {}", request.topic());
        var command = mapper.toCommand(request);
        var result = subscriptionService.createSubscription(command);
        return Response.status(Response.Status.CREATED).entity(mapper.toResponse(result)).build();
    }

    @GET
    @Operation(operationId = "getSubscriptions", summary = "List subscriptions")
    @APIResponse(responseCode = "200", description = "Subscription list",
            content = @Content(schema = @Schema(implementation = SubscriptionResponse.class, type = SchemaType.ARRAY)))
    public Response getSubscriptions() {
        log.info("Subscription LIST - user: {}", jwt.getSubject());
        List<SubscriptionResponse> subscriptions = subscriptionService.listSubscriptions()
                .stream().map(mapper::toResponse).toList();
        return Response.ok(subscriptions).build();
    }

    @GET
    @Path("/ping")
    @Operation(hidden = true)
    public Response ping() {
        return Response.ok("pong").build();
    }
}
