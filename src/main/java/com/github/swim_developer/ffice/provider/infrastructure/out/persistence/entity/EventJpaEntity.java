package com.github.swim_developer.ffice.provider.infrastructure.out.persistence.entity;

import com.github.swim_developer.framework.domain.model.EventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "ffice_events", indexes = {
        @Index(name = "idx_received_at", columnList = "receivedAt"),
        @Index(name = "idx_status", columnList = "status")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EventJpaEntity {

    @Id @Column(length = 100)
    private String eventId;

    @Column(length = 255)
    private String gufi;

    @Column(length = 50)
    private String messageType;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 25)
    @Builder.Default
    private EventStatus status = EventStatus.RECEIVED;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Builder.Default
    private int deliveredCount = 0;

    @Builder.Default
    private int retryCount = 0;

    @Column(columnDefinition = "XML", nullable = false)
    @JdbcTypeCode(SqlTypes.SQLXML)
    private String xmlMessage;
}
