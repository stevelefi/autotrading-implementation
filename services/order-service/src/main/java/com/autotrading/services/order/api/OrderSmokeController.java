package com.autotrading.services.order.api;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.core.OrderIntentState;
import com.autotrading.services.order.core.OrderSafetyEngine;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/smoke")
public class OrderSmokeController {
  private final OrderSafetyEngine orderSafetyEngine;
  private final BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub;
  private final ReliabilityMetrics reliabilityMetrics;
  private final Clock clock;

  public OrderSmokeController(OrderSafetyEngine orderSafetyEngine,
                              BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub,
                              ReliabilityMetrics reliabilityMetrics,
                              Clock clock) {
    this.orderSafetyEngine = orderSafetyEngine;
    this.brokerStub = brokerStub;
    this.reliabilityMetrics = reliabilityMetrics;
    this.clock = clock;
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "timestamp_utc", Instant.now(clock).toString(),
        "trading_mode", orderSafetyEngine.currentTradingMode().name(),
        "first_status_timeout_count", reliabilityMetrics.firstStatusTimeoutCount(),
        "alert_count", orderSafetyEngine.alertEvents().size());
  }

  @PostMapping("/timeout-drill")
  public Map<String, Object> timeoutDrill(@RequestBody(required = false) Map<String, Object> payload) {
    String idempotencyKey = payload == null || payload.get("idempotency_key") == null
        ? "smoke-timeout-" + UUID.randomUUID()
        : String.valueOf(payload.get("idempotency_key"));

    CreateOrderIntentRequest request = CreateOrderIntentRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-" + UUID.randomUUID())
            .setRequestId("req-" + UUID.randomUUID())
            .setIdempotencyKey(idempotencyKey)
            .setPrincipalId("smoke-order")
            .build())
        .setAgentId("agent-smoke")
        .setInstrumentId("eq_tqqq")
        .setSignalId("sig-" + UUID.randomUUID())
        .setDecision(Decision.DECISION_ALLOW)
        .setSide("BUY")
        .setQty(1)
        .setOrderType("MKT")
        .setTimeInForce("DAY")
        .build();

    var create = orderSafetyEngine.createOrderIntent(request, brokerStub);
    int timedOut = orderSafetyEngine.runTimeoutWatchdog(Instant.now(clock).plusSeconds(61));
    OrderIntentState orderState = orderSafetyEngine.getOrder(create.getOrderIntentId());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("trace_id", request.getRequestContext().getTraceId());
    out.put("order_intent_id", create.getOrderIntentId());
    out.put("create_status", create.getStatus().name());
    out.put("timeouts_triggered", timedOut);
    out.put("trading_mode", orderSafetyEngine.currentTradingMode().name());
    out.put("order_state", orderState == null ? "UNKNOWN" : orderState.lifecycleState().name());
    out.put("first_status_timeout_count", reliabilityMetrics.firstStatusTimeoutCount());
    out.put("alerts", orderSafetyEngine.alertEvents());
    return out;
  }

  @PostMapping("/reset")
  public Map<String, Object> reset() {
    orderSafetyEngine.resetForSmoke();
    reliabilityMetrics.reset();
    return Map.of(
        "timestamp_utc", Instant.now(clock).toString(),
        "trading_mode", orderSafetyEngine.currentTradingMode().name(),
        "first_status_timeout_count", reliabilityMetrics.firstStatusTimeoutCount(),
        "alert_count", orderSafetyEngine.alertEvents().size());
  }
}
