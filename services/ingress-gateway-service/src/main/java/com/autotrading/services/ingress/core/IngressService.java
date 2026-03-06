package com.autotrading.services.ingress.core;

import com.autotrading.libs.commonenvelope.EventEnvelope;
import com.autotrading.libs.commonenvelope.RequestContext;
import com.autotrading.libs.idempotency.ClaimOutcome;
import com.autotrading.libs.idempotency.ClaimResult;
import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.autotrading.services.ingress.db.IngressRawEventEntity;
import com.autotrading.services.ingress.db.IngressRawEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngressService {

  private static final Logger log = LoggerFactory.getLogger(IngressService.class);

  private final IdempotencyService idempotencyService;
  private final OutboxRepository outboxRepository;
  private final IngressRawEventRepository rawEventRepository;
  private final ObjectMapper objectMapper;

  public IngressService(
      IdempotencyService idempotencyService,
      OutboxRepository outboxRepository,
      IngressRawEventRepository rawEventRepository,
      ObjectMapper objectMapper) {
    this.idempotencyService = idempotencyService;
    this.outboxRepository = outboxRepository;
    this.rawEventRepository = rawEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public IngressAcceptedResponse accept(
      IngressSubmitRequest request, String requestId, String authorization) {
    validate(request, requestId, authorization);

    String payloadHash = hashPayload(request);
    ClaimResult claim = idempotencyService.claim(
        new IdempotencyClaim(request.idempotency_key(), payloadHash, Instant.now()));

    if (claim.outcome() == ClaimOutcome.CONFLICT) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency conflict");
    }

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      return rawEventRepository.findByIdempotencyKey(request.idempotency_key())
          .map(e -> toResponse(e.getIngressEventId(), e.getTraceId(), e.getReceivedAt(), "ACCEPTED"))
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.INTERNAL_SERVER_ERROR, "idempotency replay record missing"));
    }

    // New event — persist + enqueue
    String rawEventId = "raw-" + UUID.randomUUID();
    String ingressEventId = "ing-" + UUID.randomUUID();
    String traceId = "trc-" + UUID.randomUUID();
    Instant now = Instant.now();

    String payloadJson = serialize(request);
    String partitionKey = request.agent_id() != null ? request.agent_id() : request.integration_id();

    // Build the canonical event envelope for downstream consumers
    Map<String, Object> envelopePayload = new HashMap<>();
    envelopePayload.put("rawEventId", rawEventId);
    envelopePayload.put("ingressEventId", ingressEventId);
    envelopePayload.put("eventIntent", request.event_intent());
    envelopePayload.put("sourceType", "HTTP");
    envelopePayload.put("sourceEventId", request.source_event_id());
    envelopePayload.put("userPayload", request.payload());

    EventEnvelope<Map<String, Object>> envelope = new EventEnvelope<>(
        ingressEventId,
        "ingress.events.normalized.v1",
        1,
        now,
        new RequestContext(traceId, requestId, request.idempotency_key(),
            "anonymous", now),
        request.agent_id(),
        null,
        envelopePayload);

    String envelopeJson = serialize(envelope);

    IngressRawEventEntity entity = new IngressRawEventEntity(
        rawEventId,
        ingressEventId,
        traceId,
        requestId,
        request.idempotency_key(),
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

    rawEventRepository.save(entity);

    outboxRepository.append(new OutboxEvent(
        UUID.randomUUID().toString(),
        "ingress.events.normalized.v1",
        partitionKey,
        envelopeJson,
        OutboxStatus.NEW,
        0, null, now, now));

    idempotencyService.markCompleted(request.idempotency_key(), ingressEventId);

    log.info("ingress accepted ingressEventId={} traceId={} idempotencyKey={}",
        ingressEventId, traceId, request.idempotency_key());

    return toResponse(ingressEventId, traceId, now, "ACCEPTED");
  }

  private void validate(IngressSubmitRequest request, String requestId, String authorization) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body required");
    }
    if (blank(requestId) || blank(authorization)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required headers");
    }
    if (blank(request.idempotency_key()) || blank(request.event_intent()) || request.payload() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required request fields");
    }
  }

  private IngressAcceptedResponse toResponse(
      String ingressEventId, String traceId, Instant receivedAt, String status) {
    return new IngressAcceptedResponse(
        traceId,
        new IngressAcceptedResponse.Data(true, ingressEventId, receivedAt.toString(), status));
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

  private boolean blank(String s) {
    return s == null || s.isBlank();
  }
}

