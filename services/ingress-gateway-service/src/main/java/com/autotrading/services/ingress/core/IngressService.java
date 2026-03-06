package com.autotrading.services.ingress.core;

import com.autotrading.libs.idempotency.ClaimOutcome;
import com.autotrading.libs.idempotency.ClaimResult;
import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.reliability.outbox.InMemoryOutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.services.ingress.api.IngressAcceptedResponse;
import com.autotrading.services.ingress.api.IngressSubmitRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngressService {
  private final IdempotencyService idempotencyService;
  private final InMemoryOutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final Map<String, IngressEventRecord> byKey;

  public IngressService() {
    this(new InMemoryIdempotencyService(), new InMemoryOutboxRepository(), new ObjectMapper());
  }

  public IngressService(IdempotencyService idempotencyService,
                        InMemoryOutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
    this.idempotencyService = idempotencyService;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.byKey = new ConcurrentHashMap<>();
  }

  public IngressAcceptedResponse accept(IngressSubmitRequest request, String requestId, String authorization) {
    validate(request, requestId, authorization);
    String payloadHash = hashPayload(request);

    ClaimResult claim = idempotencyService.claim(new IdempotencyClaim(request.idempotency_key(), payloadHash, Instant.now()));
    if (claim.outcome() == ClaimOutcome.CONFLICT) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "idempotency conflict");
    }

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      IngressEventRecord existing = byKey.get(request.idempotency_key());
      if (existing == null) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "idempotency replay missing record");
      }
      return toResponse(existing, "ACCEPTED");
    }

    String ingressEventId = "ing-" + UUID.randomUUID();
    String traceId = "trc-" + UUID.randomUUID();
    Instant now = Instant.now();

    IngressEventRecord record = new IngressEventRecord(
        request.idempotency_key(),
        payloadHash,
        ingressEventId,
        traceId,
        now,
        "ACCEPTED");
    byKey.put(request.idempotency_key(), record);

    outboxRepository.append(new OutboxEvent(
        UUID.randomUUID().toString(),
        "ingress.events.normalized.v1",
        request.agent_id() != null ? request.agent_id() : request.integration_id(),
        serialize(request),
        OutboxStatus.NEW,
        0,
        null,
        now,
        now));

    idempotencyService.markCompleted(request.idempotency_key(), ingressEventId);
    return toResponse(record, "ACCEPTED");
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

  private IngressAcceptedResponse toResponse(IngressEventRecord record, String status) {
    return new IngressAcceptedResponse(record.traceId(), new IngressAcceptedResponse.Data(
        true,
        record.ingressEventId(),
        record.receivedAt().toString(),
        status));
  }

  private String hashPayload(IngressSubmitRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(serialize(request).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to hash ingress payload", ex);
    }
  }

  private String serialize(IngressSubmitRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize ingress payload", ex);
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public long pendingOutboxCount() {
    return outboxRepository.countPending();
  }
}
