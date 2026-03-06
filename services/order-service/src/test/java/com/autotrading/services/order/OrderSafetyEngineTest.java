package com.autotrading.services.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.SubmitOrderResponse;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.core.OrderLifecycleState;
import com.autotrading.services.order.core.OrderSafetyEngine;
import com.autotrading.services.order.core.TradingMode;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrderSafetyEngineTest {
  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  void timeoutWithoutBrokerStatusFreezesTradingMode() throws IOException {
    AtomicInteger submitCount = new AtomicInteger();
    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = startBroker(submitCount);

    ReliabilityMetrics metrics = new ReliabilityMetrics();
    Clock clock = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
    OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock);

    CreateOrderIntentRequest request = baseRequest("idem-timeout");
    var response = engine.createOrderIntent(request, brokerStub);

    int timeoutCount = engine.runTimeoutWatchdog(Instant.parse("2026-03-06T00:01:01Z"));

    assertThat(timeoutCount).isEqualTo(1);
    assertThat(engine.currentTradingMode()).isEqualTo(TradingMode.FROZEN);
    assertThat(engine.getOrder(response.getOrderIntentId()).lifecycleState()).isEqualTo(OrderLifecycleState.UNKNOWN_PENDING_RECON);
    assertThat(metrics.firstStatusTimeoutCount()).isEqualTo(1);
    assertThat(engine.alertEvents()).anyMatch(s -> s.contains("status_timeout_60s"));
    assertThat(submitCount.get()).isEqualTo(1);
  }

  @Test
  void grpcRetryWithSameKeyReturnsDuplicateWithoutSecondSubmit() throws IOException {
    AtomicInteger submitCount = new AtomicInteger();
    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = startBroker(submitCount);

    ReliabilityMetrics metrics = new ReliabilityMetrics();
    Clock clock = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
    OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock);

    CreateOrderIntentRequest request = baseRequest("idem-duplicate");
    var first = engine.createOrderIntent(request, brokerStub);
    var second = engine.createOrderIntent(request, brokerStub);

    assertThat(first.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(second.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_DUPLICATE);
    assertThat(submitCount.get()).isEqualTo(1);
  }

  @Test
  void deniedDecisionIsRejectedWithoutBrokerSubmit() throws IOException {
    AtomicInteger submitCount = new AtomicInteger();
    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = startBroker(submitCount);

    ReliabilityMetrics metrics = new ReliabilityMetrics();
    Clock clock = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
    OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock);

    CreateOrderIntentRequest deniedRequest = baseRequest("idem-denied").toBuilder()
        .setDecision(Decision.DECISION_DENY)
        .build();
    var denied = engine.createOrderIntent(deniedRequest, brokerStub);

    assertThat(denied.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_REJECTED);
    assertThat(denied.getReasonsList()).containsExactly("risk denied");
    assertThat(submitCount.get()).isZero();
  }

  @Test
  void brokerStatusAckPreventsTimeoutFreeze() throws IOException {
    AtomicInteger submitCount = new AtomicInteger();
    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = startBroker(submitCount);

    ReliabilityMetrics metrics = new ReliabilityMetrics();
    Clock clock = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
    OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock);

    var created = engine.createOrderIntent(baseRequest("idem-ack"), brokerStub);
    engine.onBrokerStatus(created.getOrderIntentId());
    int timeoutCount = engine.runTimeoutWatchdog(Instant.parse("2026-03-06T00:02:00Z"));

    assertThat(timeoutCount).isZero();
    assertThat(engine.currentTradingMode()).isEqualTo(TradingMode.NORMAL);
    assertThat(engine.getOrder(created.getOrderIntentId()).lifecycleState()).isEqualTo(OrderLifecycleState.SUBMITTED_ACKED);
    assertThat(metrics.firstStatusTimeoutCount()).isZero();
    assertThat(submitCount.get()).isEqualTo(1);
  }

  @Test
  void idempotencyConflictRejectsSecondRequestWithDifferentPayload() throws IOException {
    AtomicInteger submitCount = new AtomicInteger();
    BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub = startBroker(submitCount);

    ReliabilityMetrics metrics = new ReliabilityMetrics();
    Clock clock = Clock.fixed(Instant.parse("2026-03-06T00:00:00Z"), ZoneOffset.UTC);
    OrderSafetyEngine engine = new OrderSafetyEngine(metrics, clock);

    CreateOrderIntentRequest first = baseRequest("idem-conflict");
    CreateOrderIntentRequest conflicting = first.toBuilder().setQty(999).build();
    var accepted = engine.createOrderIntent(first, brokerStub);
    var rejected = engine.createOrderIntent(conflicting, brokerStub);

    assertThat(accepted.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_ACCEPTED);
    assertThat(rejected.getStatus()).isEqualTo(CommandStatus.COMMAND_STATUS_REJECTED);
    assertThat(rejected.getReasonsList()).containsExactly("idempotency conflict");
    assertThat(submitCount.get()).isEqualTo(1);
  }

  private BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub startBroker(AtomicInteger submitCount) throws IOException {
    String serverName = InProcessServerBuilder.generateName();
    server = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(new BrokerCommandServiceGrpc.BrokerCommandServiceImplBase() {
          @Override
          public void submitOrder(SubmitOrderRequest request, StreamObserver<SubmitOrderResponse> responseObserver) {
            submitCount.incrementAndGet();
            responseObserver.onNext(SubmitOrderResponse.newBuilder()
                .setTraceId(request.getRequestContext().getTraceId())
                .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
                .setBrokerSubmitId("broker-submit-1")
                .setSubmittedAt(Instant.now().toString())
                .build());
            responseObserver.onCompleted();
          }
        })
        .build()
        .start();

    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    return BrokerCommandServiceGrpc.newBlockingStub(channel);
  }

  private static CreateOrderIntentRequest baseRequest(String idempotencyKey) {
    return CreateOrderIntentRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-1")
            .setRequestId("req-1")
            .setIdempotencyKey(idempotencyKey)
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
