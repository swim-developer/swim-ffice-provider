package com.github.swim_developer.ffice.provider.infrastructure.in.rest;

import com.github.swim_developer.ffice.provider.infrastructure.in.rest.mapper.ProviderSubscriptionMapper;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionResponse;
import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.SubscriptionStatusUpdate;
import com.github.swim_developer.ffice.provider.application.port.in.ManageSubscriptionPort;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/swim/v1/subscriptions/{subscriptionId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "mTLS")
@Tag(name = "Subscriptions", description = "Subscription lifecycle management")
@Slf4j
public class SubscriptionItemResource {

    private final ManageSubscriptionPort subscriptionService;
    private final ProviderSubscriptionMapper mapper;
    private final JsonWebToken jwt;

    @Inject
    public SubscriptionItemResource(ManageSubscriptionPort subscriptionService,
                                    ProviderSubscriptionMapper mapper,
                                    JsonWebToken jwt) {
        this.subscriptionService = subscriptionService;
        this.mapper = mapper;
        this.jwt = jwt;
    }

    @GET
    @Operation(operationId = "getSubscription", summary = "Get subscription details")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SubscriptionResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found")
    public Response getSubscription(@PathParam("subscriptionId") @Parameter(required = true) UUID subscriptionId) {
        return Response.ok(mapper.toResponse(subscriptionService.getSubscription(subscriptionId))).build();
    }

    @PUT
    @Operation(operationId = "updateSubscriptionStatus", summary = "Update subscription status")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SubscriptionResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found")
    public Response updateSubscriptionStatus(@PathParam("subscriptionId") UUID subscriptionId,
                                             @Valid @RequestBody(required = true) SubscriptionStatusUpdate statusUpdate) {
        return Response.ok(mapper.toResponse(subscriptionService.updateStatus(subscriptionId, statusUpdate.subscriptionStatus()))).build();
    }

    @PUT
    @Path("/renew")
    @Operation(operationId = "renewSubscription", summary = "Renew subscription")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SubscriptionResponse.class)))
    @APIResponse(responseCode = "404", description = "Not found")
    public Response renewSubscription(@PathParam("subscriptionId") UUID subscriptionId) {
        return Response.ok(mapper.toResponse(subscriptionService.renewSubscription(subscriptionId, null))).build();
    }

    @DELETE
    @Operation(operationId = "unsubscribe", summary = "Delete subscription")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Not found")
    public Response unsubscribe(@PathParam("subscriptionId") UUID subscriptionId) {
        subscriptionService.deleteSubscription(subscriptionId);
        return Response.noContent().build();
    }
}
