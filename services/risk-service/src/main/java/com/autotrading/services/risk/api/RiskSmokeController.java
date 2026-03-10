package com.autotrading.services.risk.api;

import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.EvaluateSignalResponse;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.services.risk.grpc.RiskDecisionGrpcService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/smoke")
public class RiskSmokeController {
  private final RiskDecisionGrpcService riskDecisionGrpcService;

  public RiskSmokeController(RiskDecisionGrpcService riskDecisionGrpcService) {
    this.riskDecisionGrpcService = riskDecisionGrpcService;
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "timestamp_utc", Instant.now().toString(),
        "policy_audit_event_count", riskDecisionGrpcService.auditEvents().size());
  }

  @PostMapping("/command-path")
  public Map<String, Object> commandPath(@RequestBody(required = false) Map<String, Object> payload) {
    String clientEventId = payload == null || payload.get("client_event_id") == null
        ? "smoke-command-" + UUID.randomUUID()
        : String.valueOf(payload.get("client_event_id"));
    String signalId = payload == null || payload.get("signal_id") == null
        ? "sig-" + UUID.randomUUID()
        : String.valueOf(payload.get("signal_id"));
    long qty = payload == null || payload.get("qty") == null
        ? 1L
        : Long.parseLong(String.valueOf(payload.get("qty")));
    String side = payload == null || payload.get("side") == null
        ? "BUY"
        : String.valueOf(payload.get("side"));

    EvaluateSignalRequest request = EvaluateSignalRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-" + UUID.randomUUID())
            .setRequestId("req-" + UUID.randomUUID())
            .setClientEventId(clientEventId)
            .setPrincipalId("smoke-risk")
            .build())
        .setAgentId("agent-smoke")
        .setInstrumentId("eq_tqqq")
        .setSignalId(signalId)
        .setSide(side)
        .setQty(qty)
        .setStrategyTs(Instant.now().toString())
        .setOrderType("MKT")
        .setTimeInForce("DAY")
        .setReason(payload != null && payload.get("reason") != null ? String.valueOf(payload.get("reason")) : "strategy-signal")
        .setTradeEventId("trade-" + UUID.randomUUID())
        .setRawEventId("raw-" + UUID.randomUUID())
        .setOriginSourceType("TRADER_UI")
        .setOriginSourceEventId("ui-" + UUID.randomUUID())
        .setSourceSystem("agent-runtime-service")
        .build();

    EvaluateSignalResponse response = invoke(request);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("trace_id", response.getTraceId());
    out.put("decision", response.getDecision().name());
    out.put("failure_mode", response.getFailureMode().name());
    out.put("policy_version", response.getPolicyVersion());
    out.put("policy_rule_set", response.getPolicyRuleSet());
    out.put("matched_rule_ids", response.getMatchedRuleIdsList());
    out.put("deny_reasons", response.getDenyReasonsList());
    out.put("policy_audit_event_count", riskDecisionGrpcService.auditEvents().size());
    return out;
  }

  private EvaluateSignalResponse invoke(EvaluateSignalRequest request) {
    AtomicReference<EvaluateSignalResponse> responseRef = new AtomicReference<>();
    AtomicReference<Throwable> errorRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    riskDecisionGrpcService.evaluateSignal(request, new StreamObserver<>() {
      @Override
      public void onNext(EvaluateSignalResponse value) {
        responseRef.set(value);
      }

      @Override
      public void onError(Throwable throwable) {
        errorRef.set(throwable);
        latch.countDown();
      }

      @Override
      public void onCompleted() {
        latch.countDown();
      }
    });

    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "risk evaluation timeout");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "risk evaluation interrupted", ex);
    }

    Throwable error = errorRef.get();
    if (error != null) {
      if (error instanceof StatusRuntimeException statusEx) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, statusEx.getStatus().getDescription(), statusEx);
      }
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
    }

    EvaluateSignalResponse response = responseRef.get();
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "missing risk response");
    }
    return response;
  }
}
