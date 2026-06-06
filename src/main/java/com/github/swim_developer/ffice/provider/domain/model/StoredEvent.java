package com.github.swim_developer.ffice.provider.domain.model;

import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.SwimProviderEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredEvent implements SwimProviderEvent {

    private String eventId;

    private String gufi;

    private String messageType;

    @Builder.Default
    private EventStatus status = EventStatus.RECEIVED;

    @Builder.Default
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Builder.Default
    private int deliveredCount = 0;

    @Builder.Default
    private int retryCount = 0;

    private String xmlMessage;

    @Override
    public String getPayload() {
        return xmlMessage;
    }
}
