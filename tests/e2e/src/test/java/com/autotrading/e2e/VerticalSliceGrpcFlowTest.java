package com.autotrading.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.RiskDecisionServiceGrpc;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;
import com.autotrading.services.ibkr.grpc.BrokerCommandGrpcService;
import com.autotrading.services.order.core.OrderSafetyEngine;
import com.autotrading.services.order.grpc.OrderCommandGrpcService;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VerticalSliceGrpcFlowTest {
  private Server brokerServer;
  private Server orderServer;
  private Server riskServer;
  private ManagedChannel brokerChannel;
  private ManagedChannel orderChannel;
  private ManagedChannel riskChannel;

  @AfterEach
  void tearDown() {
    if (riskChannel != null) riskChannel.shutdownNow();
    if (orderChannel != null) orderChannel.shutdownNow();
    if (brokerChannel != null) brokerChannel.shutdownNow();
    if (riskServer != null) riskServer.shutdownNow();
    if (orderServer != null) orderServer.shutdownNow();
    if (brokerServer != null) brokerServer.shutdownNow();
  }

  @Test
  void riskToOrderToBrokerFlowRunsAndDedupesRetries() throws Exception {
    BrokerConnectorEngine brokerEngine = new BrokerConnectorEngine();
    String brokerName = InProcessServerBuilder.generateName();
    brokerServer = InProcessServerBuilder.forName(brokerName).directExecutor()
        .addService(new BrokerCommandGrpcService(brokerEngine))
        .build().start();
    brokerChannel = InProcessChannelBuilder.forName(brokerName).directExecutor().build();

    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = BrokerCommandServiceGrpc.newBlockingStub(brokerChannel);

    OrderSafetyEngine orderEngine = new OrderSafetyEngine(
        new ReliabilityMetrics(),
        Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC));

    String orderName = InProcessServerBuilder.generateName();
    orderServer = InProcessServerBuilder.forName(orderName).directExecutor()
        .addService(new OrderCommandGrpcService(orderEngine, brokerStub))
        .build().start();
    orderChannel = InProcessChannelBuilder.forName(orderName).directExecutor().build();

    OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub = OrderCommandServiceGrpc.newBlockingStub(orderChannel);
    RiskDecisionGrpcService riskImpl = new RiskDecisionGrpcService(orderStub, new SimplePolicyEngine());

    String riskName = InProcessServerBuilder.generateName();
    riskServer = InProcessServerBuilder.forName(riskName).directExecutor()
        .addService(riskImpl)
        .build().start();
    riskChannel = InProcessChannelBuilder.forName(riskName).directExecutor().build();

    RiskDecisionServiceGrpc.RiskDecisionServiceBlockingStub riskStub = RiskDecisionServiceGrpc.newBlockingStub(riskChannel);

    EvaluateSignalRequest request = EvaluateSignalRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-e2e")
            .setRequestId("req-e2e")
            .setIdempotencyKey("idem-e2e")
            .setPrincipalId("svc-agent-runtime")
            .build())
        .setAgentId("agent-momo-01")
        .setInstrumentId("eq_tqqq")
        .setSignalId("sig-1")
        .setSide("BUY")
        .setQty(10)
        .setStrategyTs("2026-03-06T00:00:00Z")
        .setOrderType("MKT")
        .setTimeInForce("DAY")
        .setReason("strategy-signal")
        .setTradeEventId("trade-1")
        .setRawEventId("raw-1")
        .setOriginSourceType("TRADER_UI")
        .setOriginSourceEventId("ui-1")
        .setSourceSystem("agent-runtime-service")
        .build();

    var first = riskStub.evaluateSignal(request);
    var second = riskStub.evaluateSignal(request);

    assertThat(first.getDecision().name()).isEqualTo("DECISION_ALLOW");
    assertThat(second.getDecision().name()).isEqualTo("DECISION_ALLOW");
    assertThat(brokerEngine.totalSubmitCount()).isEqualTo(1);

    assertThat(riskImpl.auditEvents()).hasSize(2);
    assertThat(orderEngine.currentTradingMode().name()).isEqualTo("NORMAL");
  }
}
