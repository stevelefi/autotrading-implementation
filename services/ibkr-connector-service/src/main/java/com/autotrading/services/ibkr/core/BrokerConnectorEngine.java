package com.autotrading.services.ibkr.core;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autotrading.command.v1.CancelOrderRequest;
import com.autotrading.command.v1.CancelOrderResponse;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.ReplaceOrderRequest;
import com.autotrading.command.v1.ReplaceOrderResponse;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.command.v1.SubmitOrderResponse;
import com.autotrading.libs.idempotency.ClaimOutcome;
import com.autotrading.libs.idempotency.ClaimResult;
import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.reliability.outbox.OutboxEvent;
import com.autotrading.libs.reliability.outbox.OutboxRepository;
import com.autotrading.libs.reliability.outbox.OutboxStatus;
import com.autotrading.services.ibkr.db.BrokerOrderEntity;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BrokerConnectorEngine {

  private static final Logger log = LoggerFactory.getLogger(BrokerConnectorEngine.class);

  private final IdempotencyService idempotencyService;
  private final BrokerOrderRepository brokerOrderRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;
  private final Map<String, SubmitOrderResponse> submitReplay = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> submitCountsByOrderIntent = new ConcurrentHashMap<>();
  private final Set<String> seenExecIds = ConcurrentHashMap.newKeySet();

  public BrokerConnectorEngine(IdempotencyService idempotencyService,
                                BrokerOrderRepository brokerOrderRepository,
                                OutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
    this.idempotencyService = idempotencyService;
    this.brokerOrderRepository = brokerOrderRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  public SubmitOrderResponse submit(SubmitOrderRequest request) {
    String key = request.getRequestContext().getIdempotencyKey();
    String payloadHash = request.getOrderIntentId() + ":" + request.getQty() + ":" + request.getOrderType();
    ClaimResult claim = idempotencyService.claim(new IdempotencyClaim(key, payloadHash, Instant.now()));

    if (claim.outcome() == ClaimOutcome.CONFLICT) {
      return SubmitOrderResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_REJECTED)
          .setBrokerSubmitId("")
          .setSubmittedAt(Instant.now().toString())
          .build();
    }

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      SubmitOrderResponse replay = submitReplay.get(key);
      if (replay != null) {
        return replay.toBuilder().setStatus(CommandStatus.COMMAND_STATUS_DUPLICATE).build();
      }
    }

    String brokerSubmitId = "broker-submit-" + UUID.randomUUID();
    Instant now = Instant.now();
    submitCountsByOrderIntent.computeIfAbsent(request.getOrderIntentId(), ignored -> new AtomicInteger()).incrementAndGet();

    SubmitOrderResponse response = SubmitOrderResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setBrokerSubmitId(brokerSubmitId)
        .setSubmittedAt(now.toString())
        .build();

    submitReplay.put(key, response);
    idempotencyService.markCompleted(key, brokerSubmitId);

    // Persist broker order + publish status event to outbox
    try {
      brokerOrderRepository.save(new BrokerOrderEntity(
          brokerSubmitId,
          request.getOrderIntentId(),
          brokerSubmitId,
          null,
          request.getAgentId(),
          request.getInstrumentId().isBlank() ? null : request.getInstrumentId(),
          request.getSide(),
          (int) request.getQty(),
          request.getOrderType(),
          "SUBMITTED",
          now, now));

      String statusPayload = objectMapper.writeValueAsString(Map.of(
          "brokerOrderId", brokerSubmitId,
          "orderIntentId", request.getOrderIntentId(),
          "brokerSubmitId", brokerSubmitId,
          "agentId", request.getAgentId(),
          "traceId", request.getRequestContext().getTraceId(),
          "status", "SUBMITTED",
          "submittedAt", now.toString()));

      outboxRepository.append(new OutboxEvent(
          "ostatus-" + UUID.randomUUID(),
          "orders.status.v1",
          request.getAgentId(),
          statusPayload,
          OutboxStatus.NEW,
          0, null, now, now));

      log.info("ibkr submit accepted brokerSubmitId={} orderIntentId={}", brokerSubmitId, request.getOrderIntentId());
    } catch (Exception e) {
      log.warn("ibkr DB/outbox persist failed orderIntentId={} cause={}", request.getOrderIntentId(), e.getMessage(), e);
    }

    return response;
  }

  public CancelOrderResponse cancel(CancelOrderRequest request) {
    return CancelOrderResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setBrokerCancelId("broker-cancel-" + UUID.randomUUID())
        .build();
  }

  public ReplaceOrderResponse replace(ReplaceOrderRequest request) {
    return ReplaceOrderResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setBrokerReplaceId("broker-replace-" + UUID.randomUUID())
        .build();
  }

  public int submitCount(String orderIntentId) {
    AtomicInteger counter = submitCountsByOrderIntent.get(orderIntentId);
    return counter == null ? 0 : counter.get();
  }

  public int totalSubmitCount() {
    return submitCountsByOrderIntent.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  public boolean recordExecution(String execId) {
    return seenExecIds.add(execId);
  }
}
