package com.github.swim_developer.integration;

import com.github.swim_developer.framework.infrastructure.testing.TestNameLoggerExtension;
import com.github.swim_developer.framework.integration.containers.ArtemisTlsContainer;
import com.github.swim_developer.framework.integration.tls.TlsTestCertificateGenerator;
import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.amqp.AmqpMessage;
import io.vertx.core.Vertx;
import io.vertx.core.net.PfxOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates AMQP over mTLS (AMQPS) connectivity for the FFICE Provider.
 *
 * <h2>SWIM-TIYP-0037 — AMQP Transport Security Authentication</h2>
 * <h2>SWIM-TIYP-0033 — TLS Server Authentication</h2>
 * <h2>SWIM-TIYP-0065 — Cryptographic Algorithms (TLS 1.2+)</h2>
 *
 * <p>
 * In production, the FFICE Provider connects to an Artemis broker using Mutual TLS
 * (AMQPS / TLS 1.2+) as mandated by EUROCONTROL SPEC-170 (Yellow Profile).
 * </p>
 *
 * <p>
 * This test uses {@link ArtemisTlsContainer} and {@link TlsTestCertificateGenerator}
 * from the SWIM Framework to spin up a real TLS-enabled broker and prove:
 * <ol>
 *   <li>A valid FFICE provider certificate establishes an AMQPS connection.</li>
 *   <li>A connection attempt without a client certificate is rejected by the broker.</li>
 *   <li>A complete send-receive cycle works over the AMQPS channel using the
 *       {@code FFICE.*} queue naming convention.</li>
 * </ol>
 * </p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(TestNameLoggerExtension.class)
@Slf4j
class FficeMtlsConnectionIT {

    private static TlsTestCertificateGenerator certs;
    private static ArtemisTlsContainer artemis;
    private static Vertx vertx;

    @BeforeAll
    static void setup() throws Exception {
        certs = TlsTestCertificateGenerator.generateAll();
        artemis = new ArtemisTlsContainer(certs);
        artemis.start();
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void teardown() {
        if (artemis != null) artemis.close();
        if (vertx != null) vertx.close();
    }

    /**
     * A provider with a valid X.509 certificate issued by the SWIM CA establishes
     * an AMQPS connection to Artemis.
     *
     * <p>This mirrors the production flow where the FFICE Provider connects to the
     * AMQ Broker using EACP (European Aviation Common PKI) certificates.</p>
     */
    @Test
    @Order(1)
    void fficeProviderConnectsWithValidMtlsCertificate() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        AmqpClient.create(vertx, buildTlsOptions(true)).connect().onComplete(conn -> {
            if (conn.failed()) {
                connected.completeExceptionally(conn.cause());
            } else {
                connected.complete(null);
                conn.result().close();
            }
        });

        connected.get(15, TimeUnit.SECONDS);
        log.info("mTLS AMQPS connection established on port {}", artemis.getAmqpsPort());
    }

    /**
     * A connection attempt without a client certificate is rejected by the broker.
     *
     * <p>The broker enforces {@code needClientAuth=true} (mutual TLS). Without a client
     * certificate the TLS handshake fails, preventing any unauthenticated access.</p>
     */
    @Test
    @Order(2)
    void connectionIsRejectedWithoutClientCertificate() {
        CompletableFuture<Void> attempt = new CompletableFuture<>();

        AmqpClient.create(vertx, buildTlsOptions(false)).connect().onComplete(conn -> {
            if (conn.failed()) {
                attempt.completeExceptionally(conn.cause());
            } else {
                conn.result().createSender("reject-probe").onComplete(send -> {
                    if (send.failed()) {
                        attempt.completeExceptionally(send.cause());
                    } else {
                        attempt.complete(null);
                    }
                });
            }
        });

        assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(Exception.class);
        log.info("Connection without client cert correctly rejected");
    }

    /**
     * A complete send-receive cycle completes over the AMQPS channel using the
     * {@code FFICE.*} queue naming convention.
     *
     * <p>This proves that the TLS layer does not interfere with AMQP message delivery.
     * In production, the FFICE Provider sends SWIM events to {@code FFICE.<user>.<subId>}
     * queues over this same mTLS connection.</p>
     */
    @Test
    @Order(3)
    void fficeEventDeliveredOverMtlsChannel() throws Exception {
        String fficeQueue = "FFICE-ansp1-sub999";
        CompletableFuture<String> received = new CompletableFuture<>();

        AmqpClient.create(vertx, buildTlsOptions(true)).connect().onComplete(conn -> {
            if (conn.failed()) { received.completeExceptionally(conn.cause()); return; }
            var connection = conn.result();

            connection.createReceiver(fficeQueue).onComplete(recv -> {
                if (recv.failed()) { received.completeExceptionally(recv.cause()); return; }
                recv.result().handler(msg -> received.complete(msg.bodyAsString()));

                connection.createSender(fficeQueue).onComplete(send -> {
                    if (send.failed()) { received.completeExceptionally(send.cause()); return; }
                    send.result().send(AmqpMessage.create()
                            .withBody("FFICE-FILED-FLIGHT-PLAN")
                            .build());
                });
            });
        });

        String result = received.get(15, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("FFICE-FILED-FLIGHT-PLAN");
        log.info("FFICE event delivered over mTLS channel: {}", result);
    }

    // ---- Helpers ----

    private AmqpClientOptions buildTlsOptions(boolean includeClientCert) {
        AmqpClientOptions opts = new AmqpClientOptions()
                .setHost(artemis.getHost())
                .setPort(artemis.getAmqpsPort())
                .setSsl(true)
                .setUsername("admin")
                .setPassword("admin")
                .setHostnameVerificationAlgorithm("")
                .setTrustOptions(new PfxOptions()
                        .setPath(certs.getTruststorePath().toString())
                        .setPassword(certs.getKeystorePassword()));

        if (includeClientCert) {
            opts.setKeyCertOptions(new PfxOptions()
                    .setPath(certs.getClientKeystorePath().toString())
                    .setPassword(certs.getKeystorePassword()));
        }
        return opts;
    }
}
