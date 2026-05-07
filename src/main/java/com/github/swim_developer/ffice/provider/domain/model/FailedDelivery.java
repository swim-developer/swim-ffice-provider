package com.github.swim_developer.ffice.provider.domain.model;

import com.github.swim_developer.framework.domain.model.SwimFailedDelivery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedDelivery implements SwimFailedDelivery {

    private Long id;
    private String eventId;
    private UUID subscriptionId;
    private String queue;
    private String errorMessage;

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private boolean resolved = false;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;
}
