package com.autotrading.services.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes system alert events from {@code system.alerts.v1} for monitoring dashboards.
 */
@Component
public class AlertEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AlertEventConsumer.class);
  private static final int MAX_ALERTS = 500;

  private final CopyOnWriteArrayList<AlertEvent> recentAlerts = new CopyOnWriteArrayList<>();
  private final ObjectMapper objectMapper;

  public AlertEventConsumer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "system.alerts.v1", groupId = "monitoring-api")
  public void onAlert(ConsumerRecord<String, String> record) {
    try {
      JsonNode root = objectMapper.readTree(record.value());
      String severity = root.path("severity").asText("INFO");
      String message = root.path("message").asText("");
      String source = root.path("source").asText("unknown");
      Instant receivedAt = Instant.now();

      AlertEvent alert = new AlertEvent(severity, message, source, receivedAt);
      recentAlerts.add(alert);
      if (recentAlerts.size() > MAX_ALERTS) {
        recentAlerts.remove(0);
      }
      log.info("monitoring received alert severity={} source={} message={}", severity, source, message);
    } catch (Exception e) {
      log.warn("monitoring failed to parse alert offset={} cause={}", record.offset(), e.getMessage());
    }
  }

  @KafkaListener(topics = "risk.events.v1", groupId = "monitoring-api-risk")
  public void onRiskEvent(ConsumerRecord<String, String> record) {
    try {
      JsonNode root = objectMapper.readTree(record.value());
      String severity = root.path("severity").asText("INFO");
      String eventType = root.path("eventType").asText("risk.event");
      String agentId = root.path("agentId").asText("unknown");
      recentAlerts.add(new AlertEvent(severity, "risk-event:" + eventType, agentId, Instant.now()));
    } catch (Exception e) {
      log.warn("monitoring failed to parse risk event offset={} cause={}", record.offset(), e.getMessage());
    }
  }

  public java.util.List<AlertEvent> getRecentAlerts() {
    return java.util.List.copyOf(recentAlerts);
  }

  public record AlertEvent(String severity, String message, String source, Instant receivedAt) {}
}
