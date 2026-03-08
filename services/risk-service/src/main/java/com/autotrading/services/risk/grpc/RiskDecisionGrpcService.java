package com.autotrading.services.risk.grpc;

import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.EvaluateSignalResponse;
import com.autotrading.command.v1.OrderCommandServiceGrpc;
import com.autotrading.command.v1.RiskDecisionServiceGrpc;
import com.autotrading.services.risk.core.PolicyAuditEvent;
import com.autotrading.services.risk.core.PolicyEvaluationResult;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import com.autotrading.services.risk.db.PolicyDecisionLogEntity;
import com.autotrading.services.risk.db.PolicyDecisionLogRepository;
import com.autotrading.services.risk.db.RiskDecisionEntity;
import com.autotrading.services.risk.db.RiskDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

/**
 * gRPC service for risk policy evaluation.
 *
 * <p>Publishes the {@code policy.evaluations.audit.v1} event directly to Kafka after
 * persisting to the DB. No outbox is used. Kafka publish failure is best-effort: the
 * audit event is logged as a warning but does not fail the gRPC response.
 */
public class RiskDecisionGrpcService extends RiskDecisionServiceGrpc.RiskDecisionServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(RiskDecisionGrpcService.class);
  private static final String AUDIT_TOPIC = "policy.evaluations.audit.v1";

  private final OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub;
  private final SimplePolicyEngine policyEngine;
  private final RiskDecisionRepository riskDecisionRepository;
  private final PolicyDecisionLogRepository policyDecisionLogRepository;
  private final DirectKafkaPublisher bestEffortPublisher;
  private final ObjectMapper objectMapper;
  private final List<PolicyAuditEvent> auditEvents = new CopyOnWriteArrayList<>();

  public RiskDecisionGrpcService(
      OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub,
      SimplePolicyEngine policyEngine,
      RiskDecisionRepository riskDecisionRepository,
      PolicyDecisionLogRepository policyDecisionLogRepository,
      DirectKafkaPublisher bestEffortPublisher,
      ObjectMapper objectMapper) {
    this.orderStub = orderStub;
    this.policyEngine = policyEngine;
    this.riskDecisionRepository = riskDecisionRepository;
    this.policyDecisionLogRepository = policyDecisionLogRepository;
    this.bestEffortPublisher = bestEffortPublisher;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void evaluateSignal(EvaluateSignalRequest request, StreamObserver<EvaluateSignalResponse> responseObserver) {
    try {
      // Set domain MDC keys for structured logging
      MDC.put("agent_id", request.getAgentId());
      MDC.put("signal_id", request.getSignalId());
      MDC.put("instrument_id", request.getInstrumentId());

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
      Instant now = Instant.now();
      String riskDecisionId = "rdec-" + UUID.randomUUID();
      String traceId = request.getRequestContext().getTraceId();

      // Persist risk decision — let exceptions propagate so @Transactional rolls back
      String denyReasonsJson = objectMapper.writeValueAsString(result.denyReasons());
      String matchedRuleIdsJson = objectMapper.writeValueAsString(result.matchedRuleIds());
      riskDecisionRepository.save(new RiskDecisionEntity(
          riskDecisionId, request.getSignalId(), traceId,
          result.decision().name(), denyReasonsJson, result.policyVersion(),
          result.policyRuleSet(), matchedRuleIdsJson, result.failureMode().name(), now));

      // Persist policy decision log
      PolicyDecisionLogEntity logEntity = new PolicyDecisionLogEntity(
          "pdl-" + UUID.randomUUID(), riskDecisionId, traceId,
          request.getAgentId(), request.getSignalId(), result.decision().name(),
          result.policyVersion(), result.policyRuleSet(), latencyMs, now);
      policyDecisionLogRepository.save(logEntity);

      // Publish audit event directly to Kafka — best-effort, does not fail the gRPC call
      String auditPayload = objectMapper.writeValueAsString(java.util.Map.of(
          "riskDecisionId", riskDecisionId,
          "signalId", request.getSignalId(),
          "traceId", traceId,
          "agentId", request.getAgentId(),
          "decision", result.decision(),
          "policyVersion", result.policyVersion(),
          "latencyMs", latencyMs));
      bestEffortPublisher.publishBestEffort(AUDIT_TOPIC, request.getAgentId(), auditPayload);

      auditEvents.add(new PolicyAuditEvent(
          traceId,
          request.getAgentId(),
          request.getSignalId(),
          result.decision(),
          result.policyVersion(),
          result.policyRuleSet(),
          result.matchedRuleIds(),
          result.denyReasons(),
          result.failureMode(),
          latencyMs,
          now));

      EvaluateSignalResponse response = EvaluateSignalResponse.newBuilder()
          .setTraceId(traceId)
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
    } finally {
      MDC.clear();
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
