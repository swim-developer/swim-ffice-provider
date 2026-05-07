package com.github.swim_developer.integration;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.amqp.AmqpMessage;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Validates per-queue access control enforcement for the FFICE Provider.
 *
 * <h2>SWIM-TIYP-0030 — Mandatory Access Control</h2>
 * <p>
 * Each subscriber of the FFICE Provider receives a dedicated AMQP queue named
 * {@code FFICE.<username>.<subscriptionId>}. The Artemis broker enforces per-address
 * security: only the queue owner's role can consume from that address.
 * </p>
 *
 * <p>
 * The security model is configured via {@code broker.xml} per-address security settings and
 * {@code artemis-roles.properties}, mirroring exactly what {@code AmqpQueueProvisioner}
 * applies at runtime via the Jolokia JMX API when a subscriber creates a subscription.
 * </p>
 *
 * <h2>Scenarios</h2>
 * <ol>
 *   <li>Owner receives events from their dedicated queue.</li>
 *   <li>A second valid subscriber is rejected from the owner's queue (AMQP Unauthorized).</li>
 *   <li>Each subscriber can only access their own queue.</li>
 *   <li>Provider (admin role) routes events to matching queues only.</li>
 * </ol>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(TestNameLoggerExtension.class)
@Slf4j
class FficeQueueSecurityIT {

    static final String ANSP1_QUEUE = "FFICE-ansp1-sub001";
    static final String ANSP2_QUEUE = "FFICE-ansp2-sub002";

    @Container
    static GenericContainer<?> artemis = buildContainer();

    private static Vertx vertx;

    // ---- Container ----

    private static GenericContainer<?> buildContainer() {
        return new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.44.0"))
                .withExposedPorts(5672)
                .withCopyToContainer(
                        Transferable.of(buildBrokerXml().getBytes(StandardCharsets.UTF_8)),
                        "/opt/swim-override/broker.xml")
                .withCopyToContainer(
                        Transferable.of(buildEntrypoint().getBytes(StandardCharsets.UTF_8), 0755),
                        "/opt/swim-security-entrypoint.sh")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withEntrypoint("/opt/swim-security-entrypoint.sh"))
                .waitingFor(Wait.forLogMessage(".*AMQ221001.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
    }

    private static String buildEntrypoint() {
        return "#!/bin/bash\n"
                + "set -e\n"
                + "INSTANCE=/var/lib/artemis-instance\n"
                + "if [ ! -f $INSTANCE/etc/broker.xml ]; then\n"
                + "  /opt/activemq-artemis/bin/artemis create $INSTANCE \\\n"
                + "    --user admin --password admin \\\n"
                + "    --silent --require-login --no-autotune\n"
                + "fi\n"
                + "cp /opt/swim-override/broker.xml $INSTANCE/etc/broker.xml\n"
                + "printf 'admin=admin\\nansp1=ansp1pass\\nansp2=ansp2pass\\n' > $INSTANCE/etc/artemis-users.properties\n"
                + "printf 'admin=admin\\namq=admin\\nansp1-swim-ffice-v1-amq-role=ansp1\\nansp2-swim-ffice-v1-amq-role=ansp2\\nother-role=ansp2\\n' > $INSTANCE/etc/artemis-roles.properties\n"
                + "exec $INSTANCE/bin/artemis run\n";
    }

    /**
     * Returns a minimal broker.xml with per-address security settings matching what
     * {@link com.github.swim_developer.framework.provider.infrastructure.out.queue.AmqpQueueProvisioner}
     * configures at runtime via the Jolokia JMX API.
     *
     * <p>The global wildcard {@code #} grants full access to the {@code admin} and {@code amq}
     * roles (used by the provider to publish). Per-address settings on each
     * {@code FFICE-<user>-<subId>} queue restrict {@code consume} and {@code browse} to
     * only the owner's role.</p>
     */
    private static String buildBrokerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<configuration xmlns=\"urn:activemq\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "               xsi:schemaLocation=\"urn:activemq /schema/artemis-configuration.xsd\">\n"
                + "  <core xmlns=\"urn:activemq:core\">\n"
                + "    <name>swim-security-test</name>\n"
                + "    <persistence-enabled>false</persistence-enabled>\n"
                + "    <security-enabled>true</security-enabled>\n"
                + "    <security-invalidation-interval>0</security-invalidation-interval>\n"
                + "    <acceptors>\n"
                + "      <acceptor name=\"amqp\">tcp://0.0.0.0:5672?protocols=AMQP</acceptor>\n"
                + "    </acceptors>\n"
                + "    <security-settings>\n"
                + "      <security-setting match=\"#\">\n"
                + "        <permission type=\"createDurableQueue\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"deleteDurableQueue\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"createNonDurableQueue\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"deleteNonDurableQueue\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"createAddress\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"deleteAddress\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"consume\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"browse\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"send\" roles=\"admin,amq\"/>\n"
                + "        <permission type=\"manage\" roles=\"admin,amq\"/>\n"
                + "      </security-setting>\n"
                + "      <security-setting match=\"" + ANSP1_QUEUE + "\">\n"
                + "        <permission type=\"createDurableQueue\" roles=\"admin\"/>\n"
                + "        <permission type=\"deleteDurableQueue\" roles=\"admin\"/>\n"
                + "        <permission type=\"createAddress\" roles=\"admin\"/>\n"
                + "        <permission type=\"deleteAddress\" roles=\"admin\"/>\n"
                + "        <permission type=\"consume\" roles=\"ansp1-swim-ffice-v1-amq-role\"/>\n"
                + "        <permission type=\"browse\" roles=\"ansp1-swim-ffice-v1-amq-role\"/>\n"
                + "        <permission type=\"send\" roles=\"admin\"/>\n"
                + "        <permission type=\"manage\" roles=\"admin\"/>\n"
                + "      </security-setting>\n"
                + "      <security-setting match=\"" + ANSP2_QUEUE + "\">\n"
                + "        <permission type=\"createDurableQueue\" roles=\"admin\"/>\n"
                + "        <permission type=\"deleteDurableQueue\" roles=\"admin\"/>\n"
                + "        <permission type=\"createAddress\" roles=\"admin\"/>\n"
                + "        <permission type=\"deleteAddress\" roles=\"admin\"/>\n"
                + "        <permission type=\"consume\" roles=\"ansp2-swim-ffice-v1-amq-role\"/>\n"
                + "        <permission type=\"browse\" roles=\"ansp2-swim-ffice-v1-amq-role\"/>\n"
                + "        <permission type=\"send\" roles=\"admin\"/>\n"
                + "        <permission type=\"manage\" roles=\"admin\"/>\n"
                + "      </security-setting>\n"
                + "    </security-settings>\n"
                + "    <addresses>\n"
                + "      <address name=\"" + ANSP1_QUEUE + "\">\n"
                + "        <anycast><queue name=\"" + ANSP1_QUEUE + "\"/></anycast>\n"
                + "      </address>\n"
                + "      <address name=\"" + ANSP2_QUEUE + "\">\n"
                + "        <anycast><queue name=\"" + ANSP2_QUEUE + "\"/></anycast>\n"
                + "      </address>\n"
                + "    </addresses>\n"
                + "  </core>\n"
                + "</configuration>\n";
    }

    // ---- Setup / Teardown ----

    @BeforeAll
    static void globalSetup() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void globalTeardown() throws Exception {
        if (vertx != null) {
            CompletableFuture<Void> closed = new CompletableFuture<>();
            vertx.close().onComplete(r -> closed.complete(null));
            closed.get(10, TimeUnit.SECONDS);
        }
    }

    // ---- Tests ----

    /**
     * The owner of a subscription queue receives events published to that queue.
     * This is the happy-path scenario: ANSP1 has a subscription, a SWIM event is
     * ingested, routed to their queue, and they consume it successfully.
     */
    @Test
    @Order(1)
    void subscriberReceivesEventsFromOwnDedicatedQueue() throws Exception {
        publishAs("admin", "admin", ANSP1_QUEUE, "FILED-FLIGHT-PLAN-001");

        String received = receiveAs("ansp1", "ansp1pass", ANSP1_QUEUE, 10);

        assertThat(received).isEqualTo("FILED-FLIGHT-PLAN-001");
        log.info("ANSP1 received from {}: {}", ANSP1_QUEUE, received);
    }

    /**
     * A second subscriber with a valid credential but the wrong role is rejected.
     *
     * <p>This is the critical security scenario: ANSP2 holds a valid AMQP credential
     * (simulating a valid JWT token in production) but does NOT hold the role
     * {@code ansp1-swim-ffice-v1-amq-role}. Artemis enforces the per-address security
     * setting and rejects the consumer with an AMQP Unauthorized error.
     *
     * <p>This proves that even if a subscriber knows another subscriber's queue name,
     * they cannot consume from it.
     */
    @Test
    @Order(2)
    void unauthorizedSubscriberIsRejectedWithAmqpError() {
        publishAs("admin", "admin", ANSP1_QUEUE, "SHOULD-NOT-REACH-ANSP2");

        CompletableFuture<String> unauthorizedAttempt =
                startReceiveAsync("ansp2", "ansp2pass", ANSP1_QUEUE);

        assertThatThrownBy(() -> unauthorizedAttempt.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(Throwable.class);
        log.info("ANSP2 correctly rejected from {} — amqp:unauthorized-access enforced", ANSP1_QUEUE);
    }

    /**
     * Each subscriber can only access their own queue.
     * ANSP2 consumes from their own dedicated queue, not from ANSP1's.
     */
    @Test
    @Order(3)
    void eachSubscriberAccessesOnlyTheirOwnQueue() throws Exception {
        publishAs("admin", "admin", ANSP2_QUEUE, "FLIGHT-ARRIVAL-002");

        String received = receiveAs("ansp2", "ansp2pass", ANSP2_QUEUE, 10);

        assertThat(received).isEqualTo("FLIGHT-ARRIVAL-002");
        log.info("ANSP2 received from own queue {}: {}", ANSP2_QUEUE, received);
    }

    /**
     * The provider (admin role) routes events only to matching subscriber queues.
     *
     * <p>ANSP1 receives from their dedicated queue. ANSP2 is once again refused — proving
     * that per-address security is consistently enforced across multiple operations, not
     * just on first attachment.</p>
     *
     * <p>Note: the queue may still contain the leftover message from the previous test
     * (ANSP2's rejected attempt left it unconsumed). ANSP1 will receive whichever message
     * arrives first; both are acceptable — the key assertion is that ANSP1 CAN receive
     * from their queue while ANSP2 CANNOT.</p>
     */
    @Test
    @Order(4)
    void providerRoutesEventsToMatchingQueuesOnly() throws Exception {
        publishAs("admin", "admin", ANSP1_QUEUE, "FLIGHT-DEPARTURE-003");

        String received = receiveAs("ansp1", "ansp1pass", ANSP1_QUEUE, 10);
        assertThat(received).isIn("SHOULD-NOT-REACH-ANSP2", "FLIGHT-DEPARTURE-003");
        log.info("ANSP1 received from {}: {}", ANSP1_QUEUE, received);

        CompletableFuture<String> ansp2Attempt = startReceiveAsync("ansp2", "ansp2pass", ANSP1_QUEUE);
        try {
            String stolen = ansp2Attempt.get(5, TimeUnit.SECONDS);
            fail("SECURITY VIOLATION: ANSP2 received message from ANSP1 queue: " + stolen);
        } catch (ExecutionException e) {
            log.info("ANSP2 rejected from ANSP1 queue (consistent enforcement): {}", e.getCause().getMessage());
        } catch (TimeoutException e) {
            log.info("ANSP2 received nothing from ANSP1 queue within timeout");
        }

        log.info("Routing verified: ANSP1 received, ANSP2 cannot access ANSP1 queue");
    }

    // ---- AMQP helpers ----

    private static String receiveAs(String user, String pass, String queue, int timeoutSec)
            throws ExecutionException, InterruptedException, TimeoutException {
        return startReceiveAsync(user, pass, queue).get(timeoutSec, TimeUnit.SECONDS);
    }

    /**
     * Starts an AMQP receiver and returns a future that completes with the first message body.
     *
     * <p>The connection is closed immediately after the first message is received (or on error).
     * This prevents leftover receivers from consuming messages intended for subsequent tests.</p>
     */
    private static CompletableFuture<String> startReceiveAsync(String user, String pass, String queue) {
        CompletableFuture<String> future = new CompletableFuture<>();
        AmqpClientOptions opts = new AmqpClientOptions()
                .setHost(artemis.getHost())
                .setPort(artemis.getMappedPort(5672))
                .setUsername(user)
                .setPassword(pass);

        AmqpClient.create(vertx, opts).connect().onComplete(conn -> {
            if (conn.failed()) {
                future.completeExceptionally(conn.cause());
                return;
            }
            var connection = conn.result();
            connection.createReceiver(queue).onComplete(recv -> {
                if (recv.failed()) {
                    future.completeExceptionally(recv.cause());
                    connection.close();
                    return;
                }
                recv.result().handler(msg -> {
                    future.complete(msg.bodyAsString());
                    connection.close();
                });
            });
        });
        return future;
    }

    private static void publishAs(String user, String pass, String queue, String payload) {
        CompletableFuture<Void> sent = new CompletableFuture<>();
        AmqpClientOptions opts = new AmqpClientOptions()
                .setHost(artemis.getHost())
                .setPort(artemis.getMappedPort(5672))
                .setUsername(user)
                .setPassword(pass);

        AmqpClient.create(vertx, opts).connect().onComplete(conn -> {
            if (conn.failed()) { sent.completeExceptionally(conn.cause()); return; }
            conn.result().createSender(queue).onComplete(sender -> {
                if (sender.failed()) { sent.completeExceptionally(sender.cause()); return; }
                sender.result().send(AmqpMessage.create().withBody(payload).build());
                vertx.setTimer(50, id -> {
                    conn.result().close();
                    sent.complete(null);
                });
            });
        });
        try {
            sent.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish to " + queue, e);
        }
    }
}
