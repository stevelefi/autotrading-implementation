package com.autotrading.services.eventprocessor.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.autotrading.libs.kafka.DirectKafkaPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.services.eventprocessor.core.EventProcessorRouter;
import com.autotrading.services.eventprocessor.core.NormalizedIngressEvent;
import com.autotrading.services.eventprocessor.core.RoutedTradeEvent;
import com.autotrading.services.eventprocessor.db.RoutedTradeEventEntity;
import com.autotrading.services.eventprocessor.db.RoutedTradeEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumes normalized ingress events from {@code ingress.events.normalized.v1},
 * routes them, persists to {@code routed_trade_events}, then publishes directly
 * to {@code trade.events.routed.v1} via {@link DirectKafkaPublisher}.
 *
 * <p>No outbox is used here. If the Kafka publish fails the exception propagates,
 * the {@code @Transactional} rolls back (including the {@code consumer_inbox} row),
 * and the Kafka consumer does not commit the offset — guaranteeing re-delivery.
 */
@Component
public class EventProcessorConsumer {

  private static final Logger log = LoggerFactory.getLogger(EventProcessorConsumer.class);
  private static final String CONSUMER_NAME = "event-processor";
  private static final String OUT_TOPIC = "trade.events.routed.v1";

  private final EventProcessorRouter router;
  private final RoutedTradeEventRepository repository;
  private final ConsumerDeduper consumerDeduper;
  private final DirectKafkaPublisher directKafkaPublisher;
  private final ObjectMapper objectMapper;

  public EventProcessorConsumer(
      EventProcessorRouter router,
      RoutedTradeEventRepository repository,
      ConsumerDeduper consumerDeduper,
      @Qualifier("directKafkaPublisher") DirectKafkaPublisher directKafkaPublisher,
      ObjectMapper objectMapper) {
    this.router = router;
    this.repository = repository;
    this.consumerDeduper = consumerDeduper;
    this.directKafkaPublisher = directKafkaPublisher;
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
      throw new RuntimeException("ingress event processing failed offset=" + record.offset(), e);
    } finally {
      MDC.clear();
    }
  }

  private void processRecord(ConsumerRecord<String, String> record) throws Exception {
    JsonNode root = objectMapper.readTree(record.value());

    String eventId = root.path("eventId").asText();
    String traceId = root.at("/context/traceId").asText("");
    String clientEventId = root.at("/context/clientEventId").asText("");
    String agentId = root.path("agentId").asText(null);

    // Set MDC for structured logging
    MDC.put("event_id", eventId);
    MDC.put("client_event_id", clientEventId);
    MDC.put("request_id", eventId);
    if (agentId != null) MDC.put("agent_id", agentId);
    Instant occurredAt;
    try {
      occurredAt = Instant.parse(root.path("occurredAt").asText());
    } catch (Exception e) {
      occurredAt = Instant.now();
    }

    JsonNode payloadNode = root.path("payload");
    String rawEventId = payloadNode.path("rawEventId").asText(eventId);
    String sourceType = payloadNode.path("sourceType").asText("HTTP");
    String sourceEventId = payloadNode.path("sourceEventId").asText(null);
    String eventIntent = payloadNode.path("eventIntent").asText("");

    @SuppressWarnings("unchecked")
    Map<String, Object> userPayload = objectMapper.convertValue(
        payloadNode.path("userPayload"), Map.class);

    final Instant finalOccurredAt = occurredAt;
    final String finalAgentId = agentId;
    final String finalSourceEventId = sourceEventId;

    // Capture the routed payload so we can publish after the deduper's transaction
    AtomicReference<String> pendingEnvelopeJson = new AtomicReference<>();
    AtomicReference<String> pendingPartitionKey = new AtomicReference<>();

    boolean processed = consumerDeduper.runOnce(CONSUMER_NAME, eventId, () -> {
      try {
        NormalizedIngressEvent normalized = new NormalizedIngressEvent(
            eventId, rawEventId, traceId, clientEventId,
            sourceType, finalSourceEventId, finalAgentId, eventIntent, finalOccurredAt, userPayload);

        Optional<RoutedTradeEvent> routed = router.route(normalized);
        if (routed.isEmpty()) {
          log.debug("event-processor skipped eventId={} (no route)", eventId);
          return;
        }

        RoutedTradeEvent rte = routed.get();
        String payloadJson = objectMapper.writeValueAsString(rte.payload());

        RoutedTradeEventEntity entity = new RoutedTradeEventEntity(
            rte.tradeEventId(), rte.rawEventId(), rte.eventId(),
            rte.traceId(), rte.clientEventId(), rte.agentId(),
            rte.sourceType(), rte.sourceEventId(), payloadJson,
            null, OUT_TOPIC, "ROUTED",
            finalOccurredAt, rte.routedAt());

        repository.save(entity);

        Map<String, Object> envelope = Map.of(
            "tradeEventId", rte.tradeEventId(),
            "eventId", eventId,
            "traceId", traceId,
            "clientEventId", clientEventId,
            "agentId", finalAgentId != null ? finalAgentId : "",
            "sourceType", sourceType,
            "sourceEventId", finalSourceEventId != null ? finalSourceEventId : "",
            "routedAt", rte.routedAt().toString(),
            "payload", rte.payload());

        String envelopeJson = objectMapper.writeValueAsString(envelope);
        pendingEnvelopeJson.set(envelopeJson);
        pendingPartitionKey.set(finalAgentId);

        log.info("event-processor routed tradeEventId={} eventId={} agentId={}",
            rte.tradeEventId(), eventId, finalAgentId);
      } catch (RuntimeException re) {
        throw re;
      } catch (Exception e) {
        throw new RuntimeException(
            "routing failed for eventId=" + eventId, e);
      }
    });

    if (!processed) {
      log.debug("event-processor duplicate suppressed eventId={}", eventId);
      return;
    }

    // Publish directly to Kafka within the same @Transactional scope.
    // If Kafka fails: exception propagates → @Transactional rolls back (consumer_inbox row
    // removed) → Kafka offset not committed → message is re-delivered for retry.
    String envelopeJson = pendingEnvelopeJson.get();
    if (envelopeJson != null) {
      directKafkaPublisher.publish(OUT_TOPIC, pendingPartitionKey.get(), envelopeJson);
    }
  }
}
