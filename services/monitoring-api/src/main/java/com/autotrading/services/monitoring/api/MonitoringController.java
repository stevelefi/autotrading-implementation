package com.autotrading.services.monitoring.api;

import com.autotrading.services.monitoring.core.IngressForwarder;
import com.autotrading.services.monitoring.core.MonitoringTradingMode;
import com.autotrading.services.monitoring.core.SystemControlState;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
  private final IngressForwarder ingressForwarder;
  private final AtomicReference<SystemControlState> controls = new AtomicReference<>(SystemControlState.initial());

  public MonitoringController(IngressForwarder ingressForwarder) {
    this.ingressForwarder = ingressForwarder;
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
    controls.set(new SystemControlState(enabled, mode, Instant.now(), actorId, requestId));
    return Map.of("trace_id", "trc-" + requestId, "data", Map.of("kill_switch", enabled ? "ON" : "OFF", "trading_mode", mode.name()));
  }

  @PostMapping("/system/trading-mode")
  public Map<String, Object> tradingMode(
      @RequestHeader("X-Actor-Id") String actorId,
      @RequestHeader("X-Request-Id") String requestId,
      @RequestBody Map<String, Object> body) {
    MonitoringTradingMode mode = MonitoringTradingMode.valueOf(String.valueOf(body.getOrDefault("trading_mode", "NORMAL")));
    SystemControlState current = controls.get();
    controls.set(new SystemControlState(current.killSwitch(), mode, Instant.now(), actorId, requestId));
    return Map.of("trace_id", "trc-" + requestId, "data", Map.of("trading_mode", mode.name()));
  }

  @GetMapping(path = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamEvents() {
    SseEmitter emitter = new SseEmitter(30_000L);
    try {
      emitter.send(SseEmitter.event().name("system.health").data(Map.of("status", "UP", "at", Instant.now().toString())));
      emitter.complete();
    } catch (Exception ex) {
      emitter.completeWithError(ex);
    }
    return emitter;
  }

  @GetMapping("/system/reconciliation/{runId}")
  public Map<String, Object> reconciliationRun(@PathVariable String runId) {
    return Map.of("run_id", runId, "status", "CLEAN", "mismatches", 0);
  }
}
