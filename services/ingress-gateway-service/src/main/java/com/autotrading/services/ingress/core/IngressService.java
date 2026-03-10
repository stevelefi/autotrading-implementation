package com.autotrading.services.ingress.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.autotrading.libs.commonenvelope.EventEnvelope;
import com.autotrading.libs.commonenvelope.RequestContext;
import com.autotrading.libs.idempotency.ClaimOutcome;
import com.autotrading.libs.idempotency.ClaimResult;
import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.kafka.KafkaFirstPublisher;
import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.autotrading.services.ingress.db.IngressRawEventEntity;
import com.autotrading.services.ingress.db.IngressRawEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Tracer;

@Service
public class IngressService {

  private static final Logger log = LoggerFactory.getLogger(IngressService.class);

  private static final String TOPIC = "ingress.events.normalized.v1";

  private final IdempotencyService idempotencyService;
  private final KafkaFirstPublisher kafkaFirstPublisher;
  private final IngressRawEventRepository rawEventRepository;
  private final ObjectMapper objectMapper;
  private final Tracer tracer;
  private final BooleanSupplier brokerHealthCheck;

  public IngressService(
      IdempotencyService idempotencyService,
      KafkaFirstPublisher kafkaFirstPublisher,
      IngressRawEventRepository rawEventRepository,
      ObjectMapper objectMapper,
      Tracer tracer) {
    this(idempotencyService, kafkaFirstPublisher, rawEventRepository, objectMapper, tracer, () -> true);
  }

  @Autowired
  public IngressService(
      IdempotencyService idempotencyService,
      KafkaFirstPublisher kafkaFirstPublisher,
      IngressRawEventRepository rawEventRepository,
      ObjectMapper objectMapper,
      Tracer tracer,
      BooleanSupplier brokerHealthCheck) {
    this.idempotencyService = idempotencyService;
    this.kafkaFirstPublisher = kafkaFirstPublisher;
    this.rawEventRepository = rawEventRepository;
    this.objectMapper = objectMapper;
    this.tracer = tracer;
    this.brokerHealthCheck = brokerHealthCheck;
  }

  /**
   * Accepts an ingress event:
   * <ol>
   *   <li>Validates and deduplicates via idempotency key.</li>
   *   <li>Always persists to {@code ingress_raw_events} for audit/troubleshooting.</li>
   *   <li>After the DB transaction commits, tries to publish to Kafka immediately.</li>
   *   <li>If Kafka is unavailable, {@link KafkaFirstPublisher} falls back to the outbox
   *       so the background poller retries with exponential back-off.</li>
   * </ol>
   */
  @Transactional
  public IngressAcceptedResponse accept(
      IngressSubmitRequest request, String requestId, String authorization) {
    validate(request, requestId, authorization);

    // Broker health gate — checked before the idempotency claim so the client may
    // retry with the same client_event_id once the broker recovers.
    if (!brokerHealthCheck.getAsBoolean()) {
      log.warn("ingress rejecting event — broker known DOWN clientEventId={}",
          request.client_event_id());
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "broker is currently unavailable — retry later");
    }

    String payloadHash = hashPayload(request);
    ClaimResult claim = idempotencyService.claim(
        new IdempotencyClaim(request.client_event_id(), payloadHash, Instant.now()));

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      return rawEventRepository.findByClientEventId(request.client_event_id())
          .map(e -> toResponse(e.getEventId(), e.getReceivedAt(), "ACCEPTED"))
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.INTERNAL_SERVER_ERROR, "idempotency replay record missing"));
    }

    // New event — persist raw for audit, then publish to Kafka post-commit
    String rawEventId = "raw-" + UUID.randomUUID();
    // event_id = OTel trace ID: the unified identity across Tempo spans, Loki logs,
    // DB records, and Kafka event envelopes — single-ID cross-layer correlation.
    String eventId = resolveTraceId();
    Instant now = Instant.now();

    String payloadJson = serialize(request);
    String partitionKey = request.agent_id() != null ? request.agent_id() : request.integration_id();

    // Build the canonical event envelope for downstream consumers
    Map<String, Object> envelopePayload = new HashMap<>();
    envelopePayload.put("rawEventId", rawEventId);
    envelopePayload.put("eventId", eventId);
    envelopePayload.put("eventIntent", request.event_intent());
    envelopePayload.put("sourceType", "HTTP");
    envelopePayload.put("sourceEventId", request.source_event_id());
    envelopePayload.put("userPayload", request.payload());

    EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
        eventId,
        TOPIC,
        1,
        now,
        new RequestContext(eventId, requestId, request.client_event_id(),
            "anonymous", now),
        request.agent_id(),
        null,
        envelopePayload);

    String envelopeJson = serialize(envelope);

    IngressRawEventEntity entity = new IngressRawEventEntity(
        rawEventId,
        eventId,
        eventId,          // trace_id column mirrors event_id (= OTel trace ID)
        requestId,
        request.client_event_id(),
        "HTTP",
        "HTTP/1.1",
        request.event_intent(),
        request.source_event_id(),
        request.agent_id(),
        request.integration_id(),
        null,             // principalJson – extracted from authorization header in future
        payloadJson,
        "ACCEPTED",
        now);

    // Always persist the raw event — provides full audit trail regardless of Kafka state
    rawEventRepository.save(entity);
    idempotencyService.markCompleted(request.client_event_id(), eventId);

    // Register a post-commit hook: try Kafka immediately; fall back to outbox on failure
    final String finalPartitionKey = partitionKey;
    final String finalEnvelopeJson = envelopeJson;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        kafkaFirstPublisher.publish(TOPIC, finalPartitionKey, finalEnvelopeJson);
      }
    });

    log.info("ingress accepted eventId={} clientEventId={}",
        eventId, request.client_event_id());

    return toResponse(eventId, now, "ACCEPTED");
  }

  private void validate(IngressSubmitRequest request, String requestId, String authorization) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body required");
    }
    if (blank(requestId) || blank(authorization)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required headers");
    }
    if (blank(request.client_event_id()) || blank(request.event_intent()) || request.payload() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required request fields");
    }
  }

  private IngressAcceptedResponse toResponse(
      String eventId, Instant receivedAt, String status) {
    return new IngressAcceptedResponse(
        eventId,
        new IngressAcceptedResponse.Data(true, receivedAt.toString(), status));
  }

  private String hashPayload(IngressSubmitRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(serialize(request).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private String serialize(IngressSubmitRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize request", e);
    }
  }

  private String serialize(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize object", e);
    }
  }

  /**
   * Returns the active OTel trace ID from the current span so it can be stored in
   * the DB and propagated through Kafka envelopes. Falls back to a random UUID when
   * there is no active span (e.g. unit tests running without the OTel agent).
   */
  private String resolveTraceId() {
    io.micrometer.tracing.Span current = tracer.currentSpan();
    if (current != null) {
      String id = current.context().traceId();
      if (id != null && !id.isBlank()) return id;
    }
    return UUID.randomUUID().toString();
  }

  private boolean blank(String s) {
    return s == null || s.isBlank();
  }
}

