package com.github.swim_developer.ffice.provider.infrastructure.in.rest;

import com.github.swim_developer.ffice.provider.infrastructure.in.rest.dto.TopicsResponse;
import com.github.swim_developer.framework.provider.application.subscription.TopicService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/swim/v1/topics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "mTLS")
@Tag(name = "Topics", description = "Available services for subscription")
@Slf4j
public class TopicResource {

    private final TopicService topicService;

    @Inject
    public TopicResource(TopicService topicService) {
        this.topicService = topicService;
    }

    @GET
    @Operation(operationId = "getTopics", summary = "List available topics")
    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TopicsResponse.class)))
    public Response getTopics() {
        return Response.ok(new TopicsResponse(topicService.getAllTopics())).build();
    }

    @GET
    @Path("/{topicId}")
    @Operation(operationId = "getTopic", summary = "Get topic")
    @APIResponse(responseCode = "200")
    @APIResponse(responseCode = "404", description = "Topic not found")
    public Response getTopic(@PathParam("topicId") String topicId) {
        return Response.ok(Map.of("topic", topicService.getTopic(topicId))).build();
    }
}
