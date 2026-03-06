package com.autotrading.services.ibkr.core;

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
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BrokerConnectorEngine {
  private final InMemoryIdempotencyService idempotencyService = new InMemoryIdempotencyService();
  private final Map<String, SubmitOrderResponse> submitReplay = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> submitCountsByOrderIntent = new ConcurrentHashMap<>();
  private final Set<String> seenExecIds = ConcurrentHashMap.newKeySet();

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
    submitCountsByOrderIntent.computeIfAbsent(request.getOrderIntentId(), ignored -> new AtomicInteger()).incrementAndGet();

    SubmitOrderResponse response = SubmitOrderResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setBrokerSubmitId(brokerSubmitId)
        .setSubmittedAt(Instant.now().toString())
        .build();

    submitReplay.put(key, response);
    idempotencyService.markCompleted(key, brokerSubmitId);
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
