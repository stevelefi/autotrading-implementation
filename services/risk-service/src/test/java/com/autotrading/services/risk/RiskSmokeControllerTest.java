package com.autotrading.services.risk;

import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.CreateOrderIntentResponse;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.EvaluateSignalResponse;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.services.risk.api.RiskSmokeController;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskSmokeControllerTest {

    private Server         orderServer;
    private ManagedChannel orderChannel;
    private OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub;

    private final SimplePolicyEngine policyEngine = new SimplePolicyEngine();

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        orderServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new OrderCommandServiceGrpc.OrderCommandServiceImplBase() {
                    @Override
                    public void createOrderIntent(CreateOrderIntentRequest request,
                                                  StreamObserver<CreateOrderIntentResponse> r) {
                        r.onNext(CreateOrderIntentResponse.newBuilder().build());
                        r.onCompleted();
                    }
                })
                .build()
                .start();
        orderChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        orderStub    = OrderCommandServiceGrpc.newBlockingStub(orderChannel);
    }

    @AfterEach
    void tearDown() {
        if (orderChannel != null) orderChannel.shutdownNow();
        if (orderServer  != null) orderServer.shutdownNow();
    }

    @Test
    void statsReturnsZeroAuditEventsInitially() {
        RiskDecisionGrpcService riskService = new RiskDecisionGrpcService(null, policyEngine);
        RiskSmokeController controller = new RiskSmokeController(riskService);

        Map<String, Object> stats = controller.stats();

        assertThat(stats.get("policy_audit_event_count")).isEqualTo(0);
        assertThat(stats).containsKey("timestamp_utc");
    }

    @Test
    void statsReflectsAuditEventCountAfterSuccessfulEvaluation() {
        RiskDecisionGrpcService riskService = new RiskDecisionGrpcService(orderStub, policyEngine);
        RiskSmokeController controller = new RiskSmokeController(riskService);

        riskService.evaluateSignal(fullRequest("idem-stats"), new StreamObserver<EvaluateSignalResponse>() {
            @Override public void onNext(EvaluateSignalResponse v) {}
            @Override public void onError(Throwable t) {}
            @Override public void onCompleted() {}
        });

        Map<String, Object> stats = controller.stats();
        assertThat(stats.get("policy_audit_event_count")).isEqualTo(1);
    }

    @Test
    void commandPathWithNullPayloadUsesDefaultsAndReturnsDecision() {
        RiskDecisionGrpcService riskService = new RiskDecisionGrpcService(orderStub, policyEngine);
        RiskSmokeController controller = new RiskSmokeController(riskService);

        Map<String, Object> result = controller.commandPath(null);

        assertThat(result).containsKey("decision");
        assertThat(result).containsKey("policy_version");
        assertThat(result.get("policy_audit_event_count")).isEqualTo(1);
    }

    @Test
    void commandPathWithExplicitPayloadUsesProvidedValues() {
        RiskDecisionGrpcService riskService = new RiskDecisionGrpcService(orderStub, policyEngine);
        RiskSmokeController controller = new RiskSmokeController(riskService);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("idempotency_key", "my-idem-key");
        payload.put("signal_id", "my-sig");
        payload.put("qty", "2");
        payload.put("side", "SELL");
        payload.put("reason", "unit-test");

        Map<String, Object> result = controller.commandPath(payload);

        assertThat(result).containsKey("trace_id");
        assertThat(result).containsKey("decision");
    }

    @Test
    void commandPathIncrementsAuditEventCount() {
        RiskDecisionGrpcService riskService = new RiskDecisionGrpcService(orderStub, policyEngine);
        RiskSmokeController controller = new RiskSmokeController(riskService);

        controller.commandPath(null);
        controller.commandPath(null);

        Map<String, Object> stats = controller.stats();
        assertThat(stats.get("policy_audit_event_count")).isEqualTo(2);
    }

    private static EvaluateSignalRequest fullRequest(String idempotencyKey) {
        return EvaluateSignalRequest.newBuilder()
                .setRequestContext(RequestContext.newBuilder()
                        .setTraceId("trc-1")
                        .setRequestId("req-1")
                        .setIdempotencyKey(idempotencyKey)
                        .setPrincipalId("svc-agent")
                        .build())
                .setAgentId("agent-1")
                .setInstrumentId("eq_tqqq")
                .setSignalId("sig-1")
                .setReason("strategy-signal")
                .setTradeEventId("trade-1")
                .setRawEventId("raw-1")
                .setSourceSystem("agent-runtime-service")
                .setOriginSourceType("TRADER_UI")
                .setOriginSourceEventId("ui-1")
                .setSide("BUY")
                .setQty(1)
                .setOrderType("MKT")
                .setTimeInForce("DAY")
                .build();
    }
}
