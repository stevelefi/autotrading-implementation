package com.autotrading.services.agentruntime.kafka;

import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.RiskDecisionServiceGrpc;
import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.services.agentruntime.core.RoutedToSignalAdapter;
import com.autotrading.services.agentruntime.db.SignalEntity;
import com.autotrading.services.agentruntime.db.SignalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes routed trade events from {@code trade.events.routed.v1},
 * builds a signal, calls risk-service gRPC to evaluate, and persists the signal.
 */
@Component
public class AgentRuntimeConsumer {

  private static final Logger log = LoggerFactory.getLogger(AgentRuntimeConsumer.class);
  private static final String CONSUMER_NAME = "agent-runtime";

  private final RoutedToSignalAdapter adapter;
  private final RiskDecisionServiceGrpc.RiskDecisionServiceBlockingStub riskStub;
  private final SignalRepository signalRepository;
  private final ConsumerDeduper consumerDeduper;
  private final ObjectMapper objectMapper;

  public AgentRuntimeConsumer(
      RoutedToSignalAdapter adapter,
      RiskDecisionServiceGrpc.RiskDecisionServiceBlockingStub riskStub,
      SignalRepository signalRepository,
      ConsumerDeduper consumerDeduper,
      ObjectMapper objectMapper) {
    this.adapter = adapter;
    this.riskStub = riskStub;
    this.signalRepository = signalRepository;
    this.consumerDeduper = consumerDeduper;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "trade.events.routed.v1", groupId = "agent-runtime")
  @Transactional
  public void onRoutedEvent(ConsumerRecord<String, String> record) {
    try {
      processRecord(record);
    } catch (Exception e) {
      log.error("agent-runtime failed to process record offset={} cause={}", record.offset(), e.getMessage(), e);
      throw new RuntimeException("agent-runtime consumer failure", e);
    }
  }

  private void processRecord(ConsumerRecord<String, String> record) throws Exception {
    JsonNode root = objectMapper.readTree(record.value());

    String tradeEventId = root.path("tradeEventId").asText();
    String ingressEventId = root.path("ingressEventId").asText(tradeEventId);
    String traceId = root.path("traceId").asText("");
    String idempotencyKey = root.path("idempotencyKey").asText("");
    String agentId = root.path("agentId").asText(null);
    String sourceType = root.path("sourceType").asText("HTTP");
    String sourceEventId = root.path("sourceEventId").asText(null);

    Instant routedAt;
    try {
      routedAt = Instant.parse(root.path("routedAt").asText());
    } catch (Exception e) {
      routedAt = Instant.now();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = objectMapper.convertValue(root.path("payload"), Map.class);

    final Instant finalRoutedAt = routedAt;
    final String finalSourceEventId = sourceEventId;

    boolean processed = consumerDeduper.runOnce(CONSUMER_NAME, tradeEventId, () -> {
      try {
        String signalId = "sig-" + UUID.randomUUID();
        String instrumentId = String.valueOf(payload.getOrDefault("instrument_id", "eq_tqqq"));

        RoutedToSignalAdapter.RoutedSignalInput input = new RoutedToSignalAdapter.RoutedSignalInput(
            traceId,
            UUID.randomUUID().toString(),
            idempotencyKey + ":risk",
            agentId != null ? agentId : "anonymous",
            agentId != null ? agentId : "anonymous",
            signalId,
            finalRoutedAt.toString(),
            tradeEventId,
            root.path("rawEventId").asText(tradeEventId),
            sourceType,
            finalSourceEventId != null ? finalSourceEventId : "",
            "agent-runtime",
            payload);

        EvaluateSignalRequest riskReq = adapter.toEvaluateSignalRequest(input);
        log.info("agent-runtime sending gRPC evaluateSignal signalId={} tradeEventId={}", signalId, tradeEventId);
        riskStub.evaluateSignal(riskReq);

        String rawPayloadJson = objectMapper.writeValueAsString(payload);
        SignalEntity entity = new SignalEntity(
            signalId, tradeEventId,
            agentId != null ? agentId : "anonymous",
            instrumentId,
            idempotencyKey,
            "AGENT_RUNTIME",
            ingressEventId,
            sourceType,
            finalSourceEventId != null ? finalSourceEventId : "",
            rawPayloadJson,
            Instant.now());
        signalRepository.save(entity);

        log.info("agent-runtime persisted signal signalId={} agentId={} tradeEventId={}",
            signalId, agentId, tradeEventId);
      } catch (RuntimeException re) {
        throw re;
      } catch (Exception e) {
        throw new RuntimeException("agent-runtime signal processing failed tradeEventId=" + tradeEventId, e);
      }
    });

    if (!processed) {
      log.debug("agent-runtime duplicate suppressed tradeEventId={}", tradeEventId);
    }
  }
}
