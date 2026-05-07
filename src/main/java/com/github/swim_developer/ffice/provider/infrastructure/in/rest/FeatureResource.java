package com.github.swim_developer.ffice.provider.infrastructure.in.rest;

import com.github.swim_developer.ffice.provider.domain.model.EventQueryFilters;
import com.github.swim_developer.ffice.provider.application.port.in.QueryEventPort;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.time.format.DateTimeParseException;

// TODO: Add domain-specific query parameters for your service
@Path("/swim/v1/features")
@Produces(MediaType.APPLICATION_XML)
@SecurityRequirement(name = "mTLS")
@Tag(name = "Request Interface (WFS)", description = "OGC Web Feature Service 2.0 for direct queries")
@Slf4j
public class FeatureResource {

    private final QueryEventPort queryService;

    @Inject
    public FeatureResource(QueryEventPort queryService) {
        this.queryService = queryService;
    }

    @GET
    @Operation(operationId = "getFeature", summary = "Request features (WFS GetFeature)")
    @APIResponse(responseCode = "200", description = "XML response with matching features")
    @APIResponse(responseCode = "400", description = "Invalid filter parameters")
    public Response getFeature(
            @QueryParam("startTime") @Parameter(description = "ISO 8601") String startTime,
            @QueryParam("endTime") @Parameter(description = "ISO 8601") String endTime,
            @QueryParam("startIndex") Integer startIndex,
            @QueryParam("count") Integer count) {

        int resolvedStartIndex = startIndex != null && startIndex >= 0 ? startIndex : 0;
        int resolvedCount = count != null && count > 0 ? count : 100;

        EventQueryFilters filters = new EventQueryFilters(
                parseInstant(startTime), parseInstant(endTime),
                resolvedStartIndex, resolvedCount);

        try {
            String result = queryService.queryFeatures(filters);
            return Response.ok(result).header("Content-Type", "application/xml; charset=UTF-8").build();
        } catch (Exception e) {
            log.error("Error executing query", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
