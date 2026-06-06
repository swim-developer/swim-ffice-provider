package com.github.swim_developer.unit;

import com.github.swim_developer.ffice.provider.application.usecase.EventDeliveryUseCase;
import com.github.swim_developer.ffice.provider.application.port.out.SubscriptionStore;
import com.github.swim_developer.ffice.provider.domain.model.FailedDelivery;
import com.github.swim_developer.ffice.provider.domain.model.StoredEvent;
import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.infrastructure.out.amqp.AmqpPublisher;
import com.github.swim_developer.framework.application.port.out.FailedDeliveryStore;
import com.github.swim_developer.framework.domain.model.DeliveryResult;
import com.github.swim_developer.framework.domain.model.EventStatus;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, TestNameLoggerExtension.class})
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class FficeEventDeliveryServiceTest {

    @InjectMocks
    private EventDeliveryUseCase deliveryService;

    @Mock
    private SubscriptionStore subscriptionRepository;

    @Mock
    private AmqpPublisher amqpPublisher;

    @Mock
    @SuppressWarnings("unchecked")
    private FailedDeliveryStore<FailedDelivery> failedDeliveryRepository;

    @Spy
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void deliversToSubscriptionWithMatchingMessageType() {
        UUID subId = UUID.randomUUID();
        Subscription sub = buildSubscription(subId, List.of("FILED_FLIGHT_PLAN"));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));

        StoredEvent event = buildEvent("MSG-001", "FILED_FLIGHT_PLAN");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.delivered()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(amqpPublisher).publishToQueue(eq(sub.getQueue()), anyString(),
                eq(QualityOfService.AT_LEAST_ONCE), eq(subId));
    }

    @Test
    void doesNotDeliverToSubscriptionWithNonMatchingMessageType() {
        UUID subId = UUID.randomUUID();
        Subscription sub = buildSubscription(subId, List.of("FILED_FLIGHT_PLAN"));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));

        StoredEvent event = buildEvent("MSG-002", "FLIGHT_ARRIVAL");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isZero();
        assertThat(result.delivered()).isZero();
        verifyNoInteractions(amqpPublisher);
    }

    @Test
    void deliversToSubscriptionWithNoMessageTypeFilter() {
        UUID subId = UUID.randomUUID();
        Subscription sub = buildSubscription(subId, List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));

        StoredEvent event = buildEvent("MSG-003", "FLIGHT_DEPARTURE");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.delivered()).isEqualTo(1);
        verify(amqpPublisher).publishToQueue(anyString(), anyString(), any(), any());
    }

    @Test
    void deliversToMultipleMatchingSubscriptions() {
        UUID subId1 = UUID.randomUUID();
        UUID subId2 = UUID.randomUUID();
        Subscription sub1 = buildSubscription(subId1, List.of("FILED_FLIGHT_PLAN", "FLIGHT_ARRIVAL"));
        Subscription sub2 = buildSubscription(subId2, List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub1, sub2));

        StoredEvent event = buildEvent("MSG-004", "FILED_FLIGHT_PLAN");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isEqualTo(2);
        assertThat(result.delivered()).isEqualTo(2);
        verify(amqpPublisher, times(2)).publishToQueue(anyString(), anyString(), any(), any());
    }

    @Test
    void handlesPublishFailureGracefully() {
        UUID subId = UUID.randomUUID();
        Subscription sub = buildSubscription(subId, List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));
        doThrow(new RuntimeException("Broker unavailable")).when(amqpPublisher)
                .publishToQueue(anyString(), anyString(), any(), any());

        StoredEvent event = buildEvent("MSG-005", "FLIGHT_PLAN_UPDATE");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.delivered()).isZero();
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void noActiveSubscriptionsReturnsZeroCounts() {
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of());

        StoredEvent event = buildEvent("MSG-006", "FILING_STATUS");

        DeliveryResult result = deliveryService.deliverToMatchingSubscriptions(event);

        assertThat(result.matched()).isZero();
        assertThat(result.delivered()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    void incrementsDeliveryCounterOnSuccess() {
        UUID subId = UUID.randomUUID();
        Subscription sub = buildSubscription(subId, List.of());
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(sub));

        deliveryService.deliverToMatchingSubscriptions(buildEvent("MSG-007", "FLIGHT_ARRIVAL"));

        assertThat(registry.counter("ffice_events_delivered_total").count()).isEqualTo(1.0);
    }

    private Subscription buildSubscription(UUID id, List<String> messageTypes) {
        return Subscription.builder()
                .subscriptionId(id)
                .queue("FFICE-user1-" + id)
                .status(SubscriptionStatus.ACTIVE)
                .qos(QualityOfService.AT_LEAST_ONCE)
                .messageType(messageTypes)
                .build();
    }

    private StoredEvent buildEvent(String messageId, String messageType) {
        return StoredEvent.builder()
                .eventId(messageId)
                .gufi("GUFI-" + UUID.randomUUID().toString().substring(0, 8))
                .messageType(messageType)
                .xmlMessage("<FficeMessage/>")
                .status(EventStatus.RECEIVED)
                .build();
    }
}
