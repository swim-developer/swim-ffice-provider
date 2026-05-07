package com.github.swim_developer.ffice.provider.domain.model;

import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.domain.model.SwimSubscription;
import com.github.swim_developer.framework.domain.model.SwimSubscriptionEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription implements SwimSubscription<FilterableEvent>, SwimSubscriptionEntity {

    @Override
    public Predicate<FilterableEvent> toFilter() {
        return event -> event != null
                && matchesList(messageType, event.messageType());
    }

    private static boolean matchesList(List<String> allowed, String value) {
        if (allowed == null || allowed.isEmpty()) return true;
        if (value == null) return false;
        return allowed.stream().anyMatch(s -> s.equalsIgnoreCase(value));
    }

    private UUID subscriptionId;
    private String topic;
    private String queue;

    @Builder.Default
    private List<String> messageType = new ArrayList<>();

    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PAUSED;

    @Builder.Default
    private QualityOfService qos = QualityOfService.AT_LEAST_ONCE;

    @Builder.Default
    private Boolean durable = true;

    private String userId;
    private String subscriptionHash;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant subscriptionEnd;
    private String description;
    private String comment;
}
