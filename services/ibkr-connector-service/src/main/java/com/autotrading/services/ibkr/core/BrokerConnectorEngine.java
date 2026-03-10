package com.autotrading.services.ibkr.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

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
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrRestClient;
import com.autotrading.services.ibkr.db.BrokerOrderEntity;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionEntity;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Core broker connector engine.
 *
 * <p>Publishes to Kafka directly after persisting to the DB. No outbox is used.
 * Kafka failures are logged and swallowed (the gRPC response is already built at
 * that point), consistent with the original try-catch pattern around persistence.
 */
public class BrokerConnectorEngine {

  private static final Logger log = LoggerFactory.getLogger(BrokerConnectorEngine.class);

  private final IdempotencyService idempotencyService;
  private final BrokerOrderRepository brokerOrderRepository;
  private final ExecutionRepository executionRepository;
  private final DirectKafkaPublisher bestEffortPublisher;
  private final ObjectMapper objectMapper;
  private final IbkrHealthProbe healthProbe;
  private final IbkrRestClient restClient;
  private final boolean simulatorMode;
  private final Map<String, SubmitOrderResponse> submitReplay = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> submitCountsByOrderIntent = new ConcurrentHashMap<>();
  private final Set<String> seenExecIds = ConcurrentHashMap.newKeySet();

  public BrokerConnectorEngine(IdempotencyService idempotencyService,
                                BrokerOrderRepository brokerOrderRepository,
                                ExecutionRepository executionRepository,
                                DirectKafkaPublisher bestEffortPublisher,
                                ObjectMapper objectMapper,
                                IbkrHealthProbe healthProbe,
                                IbkrRestClient restClient,
                                boolean simulatorMode) {
    this.idempotencyService = idempotencyService;
    this.brokerOrderRepository = brokerOrderRepository;
    this.executionRepository = executionRepository;
    this.bestEffortPublisher = bestEffortPublisher;
    this.objectMapper = objectMapper;
    this.healthProbe = healthProbe;
    this.restClient = restClient;
    this.simulatorMode = simulatorMode;
  }

  @Transactional
  public SubmitOrderResponse submit(SubmitOrderRequest request) {
    // Guard: reject immediately if broker is unreachable (REST mode only)
    if (!simulatorMode && !healthProbe.isUp()) {
      log.warn("ibkr submit rejected — broker unavailable orderIntentId={}", request.getOrderIntentId());
      return SubmitOrderResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_FAILED)
          .setBrokerSubmitId("")
          .setSubmittedAt(Instant.now().toString())
          .build();
    }

    // Namespace the key so ibkr-connector does not collide with upstream services
    // (risk, order) that also claim the same idempotency key in the shared table.
    String key = "ibkr:" + request.getRequestContext().getClientEventId();
    String payloadHash = request.getOrderIntentId() + ":" + request.getQty() + ":" + request.getOrderType();
    ClaimResult claim = idempotencyService.claim(new IdempotencyClaim(key, payloadHash, Instant.now()));

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      SubmitOrderResponse replay = submitReplay.get(key);
      if (replay != null) {
        return replay.toBuilder().setStatus(CommandStatus.COMMAND_STATUS_DUPLICATE).build();
      }
    }

    String brokerSubmitId = "broker-submit-" + UUID.randomUUID();
    String orderRef = request.getAgentId() + ":" + request.getOrderIntentId();
    Instant now = Instant.now();
    submitCountsByOrderIntent.computeIfAbsent(request.getOrderIntentId(), ignored -> new AtomicInteger()).incrementAndGet();

    // Real REST path: call IBKR CP API and capture the broker's native order ID as permId
    Long ibkrOrderId = null;
    if (!simulatorMode) {
      try {
        var ibkrResponses = restClient.submitOrder(
            request.getAgentId(),          // resolved to IBKR sub-account by BrokerAccountCache
            null,                          // conid — will be resolved from instrumentId in future
            request.getSide(),
            (int) request.getQty(),
            request.getOrderType(),
            request.getTimeInForce(),
            request.getInstrumentId(),
            orderRef);
        if (ibkrResponses != null && !ibkrResponses.isEmpty()) {
          ibkrOrderId = ibkrResponses.get(0).orderId();
          log.info("ibkr REST submit returned ibkrOrderId={} orderIntentId={}",
              ibkrOrderId, request.getOrderIntentId());
        }
      } catch (Exception e) {
        log.error("ibkr REST submitOrder failed orderIntentId={} cause={}",
            request.getOrderIntentId(), e.getMessage(), e);
        // Surface as FAILED so order-service can set CIRCUIT_OPEN
        return SubmitOrderResponse.newBuilder()
            .setTraceId(request.getRequestContext().getTraceId())
            .setStatus(CommandStatus.COMMAND_STATUS_FAILED)
            .setBrokerSubmitId("")
            .setSubmittedAt(now.toString())
            .build();
      }
    }

    SubmitOrderResponse response = SubmitOrderResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setBrokerSubmitId(brokerSubmitId)
        .setSubmittedAt(now.toString())
        .build();

    submitReplay.put(key, response);
    idempotencyService.markCompleted(key, brokerSubmitId);

    // Persist broker order, then publish order status directly to Kafka
    final Long permId = ibkrOrderId;
    try {
      brokerOrderRepository.save(new BrokerOrderEntity(
          brokerSubmitId,
          request.getOrderIntentId(),
          orderRef,
          permId != null ? permId.toString() : null,
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

      bestEffortPublisher.publishBestEffort("orders.status.v1", request.getAgentId(), statusPayload);

      log.info("ibkr submit accepted brokerSubmitId={} orderIntentId={}", brokerSubmitId, request.getOrderIntentId());
    } catch (Exception e) {
      log.warn("ibkr DB/kafka publish failed orderIntentId={} cause={}", request.getOrderIntentId(), e.getMessage(), e);
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

  /**
   * Records a fill execution from the broker callback.
   * Deduplicates by execId, persists to executions table, and publishes fills.executed.v1
   * directly to Kafka.
   *
   * @return true if this was a new execution, false if duplicate
   */
  @Transactional
  public boolean recordExecution(String execId, String orderIntentId, String brokerOrderId,
                                  String agentId, String instrumentId, String side,
                                  BigDecimal fillQty, BigDecimal fillPrice, BigDecimal commission,
                                  Instant fillTs) {
    if (!seenExecIds.add(execId)) {
      log.debug("duplicate execution ignored execId={}", execId);
      return false;
    }

    Instant now = Instant.now();
    try {
      ExecutionEntity entity = new ExecutionEntity(
          execId, orderIntentId, brokerOrderId, agentId, instrumentId,
          side, fillQty.intValue(), fillPrice,
          commission != null ? commission : BigDecimal.ZERO,
          fillTs, now);
      executionRepository.save(entity);

      String fillPayload = objectMapper.writeValueAsString(Map.of(
          "execId", execId,
          "orderIntentId", orderIntentId,
          "brokerOrderId", brokerOrderId,
          "agentId", agentId,
          "instrumentId", instrumentId != null ? instrumentId : "",
          "side", side,
          "fillQty", fillQty.toPlainString(),
          "fillPrice", fillPrice.toPlainString(),
          "commission", commission != null ? commission.toPlainString() : "0",
          "fillTs", fillTs.toString()));

      bestEffortPublisher.publishBestEffort("fills.executed.v1", agentId, fillPayload);

      log.info("execution recorded execId={} orderIntentId={} qty={} price={}",
          execId, orderIntentId, fillQty, fillPrice);
    } catch (Exception e) {
      log.error("failed to persist/publish execution execId={} cause={}", execId, e.getMessage(), e);
      seenExecIds.remove(execId); // allow retry
    }
    return true;
  }

  /** Simple overload for backward compatibility / lightweight dedup only. */
  public boolean recordExecution(String execId) {
    return seenExecIds.add(execId);
  }
}
