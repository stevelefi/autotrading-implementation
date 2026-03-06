package com.autotrading.services.order;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.SubmitOrderResponse;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.api.OrderSmokeController;
import com.autotrading.services.order.core.OrderSafetyEngine;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

class OrderSmokeControllerTest {

    private Server server;
    private ManagedChannel channel;
    private BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub;
    private ReliabilityMetrics metrics;
    private Clock clock;
    private OrderSafetyEngine engine;
    private OrderSmokeController controller;

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        AtomicInteger submitCount = new AtomicInteger();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new BrokerCommandServiceGrpc.BrokerCommandServiceImplBase() {
                    @Override
                    public void submitOrder(SubmitOrderRequest request,
                                            StreamObserver<SubmitOrderResponse> responseObserver) {
                        submitCount.incrementAndGet();
                        responseObserver.onNext(SubmitOrderResponse.newBuilder()
                                .setTraceId(request.getRequestContext().getTraceId())
                                .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
                                .setBrokerSubmitId("broker-submit-smoke")
                                .setSubmittedAt(Instant.now().toString())
                                .build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel    = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        brokerStub = BrokerCommandServiceGrpc.newBlockingStub(channel);

        metrics    = new ReliabilityMetrics();
        clock      = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
        engine     = new OrderSafetyEngine(metrics, clock);
        controller = new OrderSmokeController(engine, brokerStub, metrics, clock);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server  != null) server.shutdownNow();
    }

    // -------------------------------------------------------------------

    @Test
    void statsReturnsNormalTradingModeAndZeroCountsInitially() {
        Map<String, Object> stats = controller.stats();

        assertThat(stats.get("trading_mode")).isEqualTo("NORMAL");
        assertThat(stats.get("first_status_timeout_count")).isEqualTo(0L);
        assertThat(stats.get("alert_count")).isEqualTo(0);
        assertThat(stats).containsKey("timestamp_utc");
    }

    @Test
    void resetClearsMetricsAndReturnsNormalMode() {
        Map<String, Object> result = controller.reset();

        assertThat(result.get("trading_mode")).isEqualTo("NORMAL");
        assertThat(result.get("first_status_timeout_count")).isEqualTo(0L);
    }

    @Test
    void timeoutDrillCreatesOrderAndTriggersTimeout() {
        Map<String, Object> result = controller.timeoutDrill(
                Map.of("idempotency_key", "idem-smoke-drill"));

        assertThat(result.get("timeouts_triggered")).isEqualTo(1);
        assertThat(result.get("create_status")).isEqualTo("COMMAND_STATUS_ACCEPTED");
        assertThat(result.get("trading_mode")).isEqualTo("FROZEN");
    }

    @Test
    void timeoutDrillWithNullPayloadUsesGeneratedKey() {
        Map<String, Object> result = controller.timeoutDrill(null);

        assertThat(result.get("timeouts_triggered")).isEqualTo(1);
    }

    private static CreateOrderIntentRequest baseRequest(String idem) {
        return CreateOrderIntentRequest.newBuilder()
                .setRequestContext(RequestContext.newBuilder()
                        .setTraceId("trc-1")
                        .setRequestId("req-1")
                        .setIdempotencyKey(idem)
                        .setPrincipalId("svc-risk")
                        .build())
                .setAgentId("agent-1")
                .setInstrumentId("eq_tqqq")
                .setSignalId("sig-1")
                .setDecision(Decision.DECISION_ALLOW)
                .setSide("BUY")
                .setQty(10)
                .setOrderType("MKT")
                .setTimeInForce("DAY")
                .build();
    }
}
