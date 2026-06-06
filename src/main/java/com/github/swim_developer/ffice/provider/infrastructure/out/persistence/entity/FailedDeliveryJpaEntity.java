package com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failed_deliveries", indexes = {
        @Index(name = "idx_fd_event_id", columnList = "eventId"),
        @Index(name = "idx_fd_resolved", columnList = "resolved")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FailedDeliveryJpaEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 255)
    private String queue;

    @Column(length = 1000)
    private String errorMessage;

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private boolean resolved = false;

    @Column(nullable = false, updatable = false) @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;
}
