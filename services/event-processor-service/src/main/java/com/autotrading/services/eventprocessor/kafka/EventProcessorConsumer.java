package com.autotrading.services.eventprocessor.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.services.eventprocessor.core.EventProcessorRouter;
import com.autotrading.services.eventprocessor.core.NormalizedIngressEvent;
import com.autotrading.services.eventprocessor.core.RoutedTradeEvent;
import com.autotrading.services.eventprocessor.db.RoutedTradeEventEntity;
import com.autotrading.services.eventprocessor.db.RoutedTradeEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumes normalized ingress events from {@code ingress.events.normalized.v1},
 * routes them with {@link EventProcessorRouter}, persists the result, and
 * publishes the routed event to the outbox for relay to {@code trade.events.routed.v1}.
 */
@Component
public class EventProcessorConsumer {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorConsumer.class);
  private static final String CONSUMER_NAME = "event-processor";

  private final EventProcessorRouter router;
  private final RoutedTradeEventRepository repository;
  private final OutboxRepository outboxRepository;
  private final ConsumerDeduper consumerDeduper;
  private final ObjectMapper objectMapper;

  public EventProcessorConsumer(
      EventProcessorRouter router,
      RoutedTradeEventRepository repository,
      OutboxRepository outboxRepository,
      ConsumerDeduper consumerDeduper,
      ObjectMapper objectMapper) {
    this.router = router;
    this.repository = repository;
    this.outboxRepository = outboxRepository;
    this.consumerDeduper = consumerDeduper;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "ingress.events.normalized.v1", groupId = "${spring.kafka.consumer.group-id}")
  @Transactional
  public void onIngressEvent(ConsumerRecord<String, String> record) {
    try {
      processRecord(record);
    } catch (Exception e) {
      log.error("failed to process ingress event offset={} key={}: {}",
          record.offset(), record.key(), e.getMessage(), e);
      // do not re-throw — let Kafka commit the offset (dead-letter in future)
    }
  }

  private void processRecord(ConsumerRecord<String, String> record) throws Exception {
    JsonNode root = objectMapper.readTree(record.value());

    String ingressEventId = root.path("eventId").asText();
    String traceId = root.at("/context/traceId").asText("");
    String idempotencyKey = root.at("/context/idempotencyKey").asText("");
    String agentId = root.path("agentId").asText(null);
    Instant occurredAt;
    try {
      occurredAt = Instant.parse(root.path("occurredAt").asText());
    } catch (Exception e) {
      occurredAt = Instant.now();
    }

    JsonNode payloadNode = root.path("payload");
    String rawEventId = payloadNode.path("rawEventId").asText(ingressEventId);
    String sourceType = payloadNode.path("sourceType").asText("HTTP");
    String sourceEventId = payloadNode.path("sourceEventId").asText(null);
    String eventIntent = payloadNode.path("eventIntent").asText("");

    @SuppressWarnings("unchecked")
    Map<String, Object> userPayload = objectMapper.convertValue(
        payloadNode.path("userPayload"), Map.class);

    final Instant finalOccurredAt = occurredAt;
    final String finalAgentId = agentId;
    final String finalSourceEventId = sourceEventId;

    boolean processed = consumerDeduper.runOnce(CONSUMER_NAME, ingressEventId, () -> {
      try {
        NormalizedIngressEvent normalized = new NormalizedIngressEvent(
            ingressEventId, rawEventId, traceId, idempotencyKey,
            sourceType, finalSourceEventId, finalAgentId, eventIntent, finalOccurredAt, userPayload);

        Optional<RoutedTradeEvent> routed = router.route(normalized);
        if (routed.isEmpty()) {
          log.debug("event-processor skipped ingressEventId={} (no route)", ingressEventId);
          return;
        }

        RoutedTradeEvent rte = routed.get();
        String payloadJson = objectMapper.writeValueAsString(rte.payload());

        RoutedTradeEventEntity entity = new RoutedTradeEventEntity(
            rte.tradeEventId(), rte.rawEventId(), rte.ingressEventId(),
            rte.traceId(), rte.idempotencyKey(), rte.agentId(),
            rte.sourceType(), rte.sourceEventId(), payloadJson,
            null, "trade.events.routed.v1", "ROUTED",
            finalOccurredAt, rte.routedAt());

        repository.save(entity);

        Map<String, Object> envelope = Map.of(
            "tradeEventId", rte.tradeEventId(),
            "ingressEventId", ingressEventId,
            "traceId", traceId,
            "idempotencyKey", idempotencyKey,
            "agentId", finalAgentId != null ? finalAgentId : "",
            "sourceType", sourceType,
            "sourceEventId", finalSourceEventId != null ? finalSourceEventId : "",
            "routedAt", rte.routedAt().toString(),
            "payload", rte.payload());

        String envelopeJson = objectMapper.writeValueAsString(envelope);
        Instant now = Instant.now();
        outboxRepository.append(new OutboxEvent(
            UUID.randomUUID().toString(),
            "trade.events.routed.v1",
            finalAgentId,
            envelopeJson,
            OutboxStatus.NEW,
            0, null, now, now));

        log.info("event-processor routed tradeEventId={} ingressEventId={} agentId={}",
            rte.tradeEventId(), ingressEventId, finalAgentId);
      } catch (RuntimeException re) {
        throw re;
      } catch (Exception e) {
        throw new RuntimeException(
            "routing failed for ingressEventId=" + ingressEventId, e);
      }
    });

    if (!processed) {
      log.debug("event-processor duplicate suppressed ingressEventId={}", ingressEventId);
    }
  }
}
