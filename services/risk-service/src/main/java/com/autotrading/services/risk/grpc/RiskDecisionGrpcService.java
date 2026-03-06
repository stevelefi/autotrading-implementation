package com.autotrading.services.risk.grpc;

import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.EvaluateSignalResponse;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.command.v1.RiskDecisionServiceGrpc;
import com.autotrading.services.risk.core.PolicyAuditEvent;
import com.autotrading.services.risk.core.PolicyEvaluationResult;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RiskDecisionGrpcService extends RiskDecisionServiceGrpc.RiskDecisionServiceImplBase {
  private final OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub;
  private final SimplePolicyEngine policyEngine;
  private final List<PolicyAuditEvent> auditEvents = new CopyOnWriteArrayList<>();

  public RiskDecisionGrpcService(OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub,
                                 SimplePolicyEngine policyEngine) {
    this.orderStub = orderStub;
    this.policyEngine = policyEngine;
  }

  @Override
  public void evaluateSignal(EvaluateSignalRequest request, StreamObserver<EvaluateSignalResponse> responseObserver) {
    try {
      validateLineage(request);

      Instant started = Instant.now();
      PolicyEvaluationResult result = policyEngine.evaluate(request);

      CreateOrderIntentRequest command = CreateOrderIntentRequest.newBuilder()
          .setRequestContext(request.getRequestContext())
          .setAgentId(request.getAgentId())
          .setInstrumentId(request.getInstrumentId())
          .setSignalId(request.getSignalId())
          .setDecision(result.decision())
          .addAllDenyReasons(result.denyReasons())
          .setPolicyVersion(result.policyVersion())
          .setPolicyRuleSet(result.policyRuleSet())
          .addAllMatchedRuleIds(result.matchedRuleIds())
          .setFailureMode(result.failureMode())
          .setTradeEventId(request.getTradeEventId())
          .setRawEventId(request.getRawEventId())
          .setOriginSourceType(request.getOriginSourceType())
          .setOriginSourceEventId(request.getOriginSourceEventId())
          .setSide(request.getSide())
          .setQty(request.getQty())
          .setOrderType(request.getOrderType())
          .setTimeInForce(request.getTimeInForce())
          .build();

      orderStub.createOrderIntent(command);

      long latencyMs = Duration.between(started, Instant.now()).toMillis();
      auditEvents.add(new PolicyAuditEvent(
          request.getRequestContext().getTraceId(),
          request.getAgentId(),
          request.getSignalId(),
          result.decision(),
          result.policyVersion(),
          result.policyRuleSet(),
          result.matchedRuleIds(),
          result.denyReasons(),
          result.failureMode(),
          latencyMs,
          Instant.now()));

      EvaluateSignalResponse response = EvaluateSignalResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setDecision(result.decision())
          .addAllDenyReasons(result.denyReasons())
          .setPolicyVersion(result.policyVersion())
          .setPolicyRuleSet(result.policyRuleSet())
          .addAllMatchedRuleIds(result.matchedRuleIds())
          .setFailureMode(result.failureMode())
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (IllegalArgumentException ex) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
    } catch (Exception ex) {
      responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  private void validateLineage(EvaluateSignalRequest request) {
    if (blank(request.getTradeEventId())
        || blank(request.getRawEventId())
        || blank(request.getSourceSystem())
        || blank(request.getOriginSourceType())) {
      throw new IllegalArgumentException("missing required lineage/source attribution fields");
    }
    if ("EXTERNAL_SYSTEM".equals(request.getOriginSourceType()) && blank(request.getOriginSourceEventId())) {
      throw new IllegalArgumentException("origin_source_event_id required for external system source");
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public List<PolicyAuditEvent> auditEvents() {
    return List.copyOf(auditEvents);
  }
}
