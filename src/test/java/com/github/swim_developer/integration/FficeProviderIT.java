package com.github.swim_developer.integration;

import com.github.swim_developer.ffice.provider.domain.model.Subscription;
import com.github.swim_developer.ffice.provider.infrastructure.out.persistence.JpaSubscriptionStore;
import com.github.swim_developer.framework.application.port.out.QueueProvisioningStrategy;
import com.github.swim_developer.framework.domain.model.QualityOfService;
import com.github.swim_developer.framework.domain.model.SubscriptionStatus;
import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.provider.infrastructure.out.security.JwtRoleValidator;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestSecurity(user = "it-test-user", roles = "user")
@ExtendWith(TestNameLoggerExtension.class)
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class FficeProviderIT {

    private static final String SUBSCRIPTIONS_PATH = "/swim/v1/subscriptions";
    private static final String TOPICS_PATH = "/swim/v1/topics";
    private static final String TEST_USER = "it-test-user";
    private static final String FFICE_TOPIC = "FficeService";

    @InjectMock
    JwtRoleValidator jwtRoleValidator;

    @InjectMock
    QueueProvisioningStrategy queueProvisioner;

    @Inject
    JpaSubscriptionStore subscriptionStore;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> subscriptionStore.deleteAll());
        reset(jwtRoleValidator, queueProvisioner);
        when(jwtRoleValidator.getUsername()).thenReturn(TEST_USER);
        doNothing().when(jwtRoleValidator).validateAmqRole(anyString());
        doNothing().when(queueProvisioner).createQueue(anyString());
        doNothing().when(queueProvisioner).addSecurityRole(anyString(), anyString(), anyString());
        doNothing().when(queueProvisioner).removeQueue(anyString());
        doNothing().when(queueProvisioner).removeSecurityRole(anyString());
    }

    @Test
    @Order(1)
    void createSubscriptionReturns201WithPausedStatus() {
        var body = Map.of(
                "topic", FFICE_TOPIC,
                "qos", "AT_LEAST_ONCE",
                "description", "Integration test"
        );

        var response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath();

        assertThat(response.getString("subscription_id")).isNotNull();
        assertThat(response.getString("topic")).isEqualTo(FFICE_TOPIC);
        assertThat(response.getString("queue")).startsWith("FFICE-");
        assertThat(response.getString("subscription_status")).isEqualTo(SubscriptionStatus.PAUSED.name());

        verify(queueProvisioner, atLeast(1)).createQueue(anyString());
        verify(queueProvisioner, atLeast(1)).addSecurityRole(anyString(), anyString(), anyString());
    }

    @Test
    @Order(2)
    void createSubscriptionWithMessageTypeFilterIsPersisted() {
        var body = Map.of(
                "topic", FFICE_TOPIC,
                "message_type", List.of("FILED_FLIGHT_PLAN", "FLIGHT_ARRIVAL"),
                "description", "FF-ICE filter test"
        );

        var response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath();

        assertThat(response.getString("subscription_id")).isNotNull();
        assertThat(response.getString("queue")).startsWith("FFICE-");
        assertThat(response.getList("message_type", String.class))
                .containsExactlyInAnyOrder("FILED_FLIGHT_PLAN", "FLIGHT_ARRIVAL");

        String subscriptionId = response.getString("subscription_id");
        var persisted = subscriptionStore.findSubscriptionById(UUID.fromString(subscriptionId));
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getMessageType())
                .containsExactlyInAnyOrder("FILED_FLIGHT_PLAN", "FLIGHT_ARRIVAL");
    }

    @Test
    @Order(3)
    void createSubscriptionWithInvalidTopicReturns400() {
        var body = Map.of("topic", "NonExistentTopic", "qos", "AT_LEAST_ONCE");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(4)
    void idempotencyReturnsSameSubscriptionForDuplicateRequest() {
        var body = Map.of(
                "topic", FFICE_TOPIC,
                "description", "Idempotency test"
        );

        var first = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath();

        var second = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath();

        assertThat(first.getString("subscription_id"))
                .isEqualTo(second.getString("subscription_id"));
        assertThat(first.getString("queue"))
                .isEqualTo(second.getString("queue"));
    }

    @Test
    @Order(5)
    void getSubscriptionReturnsDetails() {
        String subscriptionId = createTestSubscription();

        var response = given()
                .when()
                .get(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(response.getString("subscription_id")).isEqualTo(subscriptionId);
        assertThat(response.getString("topic")).isEqualTo(FFICE_TOPIC);
        assertThat(response.getString("subscription_status")).isEqualTo(SubscriptionStatus.PAUSED.name());
    }

    @Test
    @Order(6)
    void getSubscriptionReturns404ForUnknownId() {
        given()
                .when()
                .get(SUBSCRIPTIONS_PATH + "/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    void listSubscriptionsReturnsUserSubscriptions() {
        createTestSubscription();

        var response = given()
                .when()
                .get(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(response.getList("$")).isNotEmpty();
    }

    @Test
    @Order(8)
    void activateSubscriptionTransition() {
        String subscriptionId = createTestSubscription();

        var response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("subscription_status", SubscriptionStatus.ACTIVE.name()))
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(response.getString("subscription_status")).isEqualTo(SubscriptionStatus.ACTIVE.name());

        var persisted = subscriptionStore.findSubscriptionById(UUID.fromString(subscriptionId));
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @Order(9)
    void pauseActiveSubscription() {
        String subscriptionId = createTestSubscription();
        activateSubscription(subscriptionId);

        var response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("subscription_status", SubscriptionStatus.PAUSED.name()))
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(response.getString("subscription_status")).isEqualTo(SubscriptionStatus.PAUSED.name());
    }

    @Test
    @Order(10)
    void deleteSubscriptionSoftDeletes() {
        String subscriptionId = createTestSubscription();

        given()
                .when()
                .delete(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(204);

        var persisted = subscriptionStore.findSubscriptionById(UUID.fromString(subscriptionId));
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(SubscriptionStatus.DELETED);
    }

    @Test
    @Order(11)
    void deletedSubscriptionIsImmutable() {
        String subscriptionId = createTestSubscription();
        given().when().delete(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId).then().statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("subscription_status", SubscriptionStatus.ACTIVE.name()))
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(12)
    void renewSubscriptionExtendsTtl() {
        String subscriptionId = createTestSubscription();

        var createResponse = given()
                .when()
                .get(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath();
        Instant before = Instant.parse(createResponse.getString("subscription_end"));

        var renewResponse = given()
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}/renew", subscriptionId)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(renewResponse.getString("subscription_id")).isEqualTo(subscriptionId);
        Instant after = Instant.parse(renewResponse.getString("subscription_end"));
        assertThat(after).isAfter(before);
    }

    @Test
    @Order(13)
    void renewDeletedSubscriptionReturns400() {
        String subscriptionId = createTestSubscription();
        given().when().delete(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId).then().statusCode(204);

        given()
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}/renew", subscriptionId)
                .then()
                .statusCode(400);
    }

    @Test
    @Order(14)
    void deleteNonExistentSubscriptionReturns404() {
        given()
                .when()
                .delete(SUBSCRIPTIONS_PATH + "/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    @Order(15)
    void getSubscriptionOwnedByAnotherUserReturns403() {
        UUID id = UUID.randomUUID();
        Subscription foreign = Subscription.builder()
                .subscriptionId(id)
                .topic(FFICE_TOPIC)
                .queue("FFICE-foreign-" + id.toString().substring(0, 8))
                .userId("other-user")
                .subscriptionHash("hash-foreign-" + id)
                .subscriptionEnd(Instant.now().plus(Duration.ofHours(24)))
                .status(SubscriptionStatus.PAUSED)
                .qos(QualityOfService.AT_LEAST_ONCE)
                .durable(true)
                .build();
        QuarkusTransaction.requiringNew().run(() -> subscriptionStore.persist(foreign));

        given()
                .when()
                .get(SUBSCRIPTIONS_PATH + "/{id}", id)
                .then()
                .statusCode(403);
    }

    @Test
    @Order(16)
    void deleteSubscriptionOwnedByAnotherUserReturns403() {
        UUID id = UUID.randomUUID();
        Subscription foreign = Subscription.builder()
                .subscriptionId(id)
                .topic(FFICE_TOPIC)
                .queue("FFICE-foreign-del-" + id.toString().substring(0, 8))
                .userId("other-user")
                .subscriptionHash("hash-foreign-del-" + id)
                .subscriptionEnd(Instant.now().plus(Duration.ofHours(24)))
                .status(SubscriptionStatus.PAUSED)
                .qos(QualityOfService.AT_LEAST_ONCE)
                .durable(true)
                .build();
        QuarkusTransaction.requiringNew().run(() -> subscriptionStore.persist(foreign));

        given()
                .when()
                .delete(SUBSCRIPTIONS_PATH + "/{id}", id)
                .then()
                .statusCode(403);

        assertThat(subscriptionStore.findSubscriptionById(id)).isPresent();
        assertThat(subscriptionStore.findSubscriptionById(id).get().getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    @Order(20)
    void listTopicsReturnsConfiguredTopics() {
        var response = given()
                .when()
                .get(TOPICS_PATH)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertThat(response.getList("topics")).contains(FFICE_TOPIC, "TestTopic");
    }

    @Test
    @Order(21)
    void getTopicReturnsExistingTopic() {
        given()
                .when()
                .get(TOPICS_PATH + "/{topicId}", FFICE_TOPIC)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(22)
    void getUnknownTopicReturns404() {
        given()
                .when()
                .get(TOPICS_PATH + "/{topicId}", "NonExistentTopic")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(30)
    void pingReturns200() {
        given()
                .when()
                .get(SUBSCRIPTIONS_PATH + "/ping")
                .then()
                .statusCode(200);
    }

    private String createTestSubscription() {
        var body = Map.of(
                "topic", FFICE_TOPIC,
                "description", "IT test - " + UUID.randomUUID().toString().substring(0, 8)
        );

        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(SUBSCRIPTIONS_PATH)
                .then()
                .statusCode(201)
                .extract().jsonPath().getString("subscription_id");
    }

    private void activateSubscription(String subscriptionId) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("subscription_status", SubscriptionStatus.ACTIVE.name()))
                .when()
                .put(SUBSCRIPTIONS_PATH + "/{id}", subscriptionId)
                .then()
                .statusCode(200);
    }
}
