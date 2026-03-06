package com.autotrading.services.monitoring.api;

import com.autotrading.services.monitoring.core.IngressForwarder;
import com.autotrading.services.monitoring.core.MonitoringTradingMode;
import com.autotrading.services.monitoring.core.SystemControlState;
import com.autotrading.services.monitoring.db.SystemControlEntity;
import com.autotrading.services.monitoring.db.SystemControlRepository;
import com.autotrading.services.monitoring.kafka.AlertEventConsumer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class MonitoringController {

  private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

  private final IngressForwarder ingressForwarder;
  private final SystemControlRepository systemControlRepository;
  private final AlertEventConsumer alertEventConsumer;
  private final AtomicReference<SystemControlState> controls = new AtomicReference<>(SystemControlState.initial());

  public MonitoringController(IngressForwarder ingressForwarder,
                               SystemControlRepository systemControlRepository,
                               AlertEventConsumer alertEventConsumer) {
    this.ingressForwarder = ingressForwarder;
    this.systemControlRepository = systemControlRepository;
    this.alertEventConsumer = alertEventConsumer;

    // Restore persisted system controls on startup
    try {
      boolean killSwitch = systemControlRepository.findById("KILL_SWITCH")
          .map(e -> "ON".equals(e.getControlValue()))
          .orElse(false);
      MonitoringTradingMode mode = systemControlRepository.findById("TRADING_MODE")
          .map(e -> MonitoringTradingMode.valueOf(e.getControlValue()))
          .orElse(MonitoringTradingMode.NORMAL);
      if (killSwitch) mode = MonitoringTradingMode.FROZEN;
      controls.set(new SystemControlState(killSwitch, mode, Instant.now(), "system", "db-restore"));
      log.info("monitoring restored controls from DB killSwitch={} tradingMode={}", killSwitch, mode);
    } catch (Exception e) {
      log.warn("monitoring failed to restore controls from DB, using defaults: {}", e.getMessage());
    }
  }

  @PostMapping("/trade-events/manual")
  public ResponseEntity<Map<String, Object>> manualTradeEvent(
      @RequestHeader("X-Actor-Id") String actorId,
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody Map<String, Object> body) {
    String idempotencyKey = String.valueOf(body.get("idempotency_key"));
    Map<String, Object> forwarded = ingressForwarder.forward(idempotencyKey, body, "TRADER_UI");
    return ResponseEntity.accepted().body(Map.of("trace_id", "trc-" + requestId, "data", forwarded));
  }

  @PostMapping("/trade-events/external")
  public ResponseEntity<Map<String, Object>> externalTradeEvent(
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody Map<String, Object> body) {
    String idempotencyKey = String.valueOf(body.get("idempotency_key"));
    Map<String, Object> forwarded = ingressForwarder.forward(idempotencyKey, body, "EXTERNAL_SYSTEM");
    return ResponseEntity.accepted().body(Map.of("trace_id", "trc-" + requestId, "data", forwarded));
  }

  @GetMapping("/system/health")
  public Map<String, Object> systemHealth() {
    return Map.of("status", "UP", "timestamp_utc", Instant.now().toString());
  }

  @GetMapping("/system/consistency-status")
  public Map<String, Object> consistencyStatus() {
    SystemControlState state = controls.get();
    return Map.of(
        "trading_mode", state.tradingMode().name(),
        "kill_switch", state.killSwitch(),
        "updated_at", state.updatedAtUtc().toString());
  }

  @PostMapping("/system/kill-switch")
  public Map<String, Object> killSwitch(
      @RequestHeader("X-Actor-Id") String actorId,
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody Map<String, Object> body) {
    boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", false)));
    MonitoringTradingMode mode = enabled ? MonitoringTradingMode.FROZEN : controls.get().tradingMode();
    Instant now = Instant.now();
    controls.set(new SystemControlState(enabled, mode, now, actorId, requestId));

    persistControl("KILL_SWITCH", enabled ? "ON" : "OFF", actorId, requestId, now);
    log.info("kill-switch set to {} by actorId={}", enabled ? "ON" : "OFF", actorId);

    return Map.of("trace_id", "trc-" + requestId, "data",
        Map.of("kill_switch", enabled ? "ON" : "OFF", "trading_mode", mode.name()));
  }

  @PostMapping("/system/trading-mode")
  public Map<String, Object> tradingMode(
      @RequestHeader("X-Actor-Id") String actorId,
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody Map<String, Object> body) {
    MonitoringTradingMode mode = MonitoringTradingMode.valueOf(
        String.valueOf(body.getOrDefault("trading_mode", "NORMAL")));
    SystemControlState current = controls.get();
    Instant now = Instant.now();
    controls.set(new SystemControlState(current.killSwitch(), mode, now, actorId, requestId));

    persistControl("TRADING_MODE", mode.name(), actorId, requestId, now);
    log.info("trading mode set to {} by actorId={}", mode, actorId);

    return Map.of("trace_id", "trc-" + requestId, "data", Map.of("trading_mode", mode.name()));
  }

  @GetMapping(path = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamEvents() {
    SseEmitter emitter = new SseEmitter(30_000L);
    try {
      emitter.send(SseEmitter.event().name("system.health").data(Map.of("status", "UP", "at", Instant.now().toString())));
      List<AlertEventConsumer.AlertEvent> alerts = alertEventConsumer.getRecentAlerts();
      if (!alerts.isEmpty()) {
        AlertEventConsumer.AlertEvent last = alerts.get(alerts.size() - 1);
        emitter.send(SseEmitter.event().name("system.alert").data(Map.of(
            "severity", last.severity(),
            "message", last.message(),
            "source", last.source(),
            "at", last.receivedAt().toString())));
      }
      emitter.complete();
    } catch (Exception ex) {
      emitter.completeWithError(ex);
    }
    return emitter;
  }

  @GetMapping("/system/alerts")
  public Map<String, Object> recentAlerts() {
    return Map.of("alerts", alertEventConsumer.getRecentAlerts().stream()
        .map(a -> Map.of(
            "severity", a.severity(),
            "message", a.message(),
            "source", a.source(),
            "receivedAt", a.receivedAt().toString()))
        .toList());
  }

  @GetMapping("/system/reconciliation/{runId}")
  public Map<String, Object> reconciliationRun(@PathVariable String runId) {
    return Map.of("run_id", runId, "status", "CLEAN", "mismatches", 0);
  }

  private void persistControl(String key, String value, String actorId, String traceId, Instant now) {
    SystemControlEntity entity = systemControlRepository.findById(key)
        .orElseGet(() -> new SystemControlEntity(key, value, actorId, traceId, now));
    entity.setControlValue(value);
    entity.setActorId(actorId);
    entity.setTraceId(traceId);
    entity.setUpdatedAt(now);
    systemControlRepository.save(entity);
  }
}
