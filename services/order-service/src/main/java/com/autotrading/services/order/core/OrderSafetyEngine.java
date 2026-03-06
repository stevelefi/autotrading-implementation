package com.autotrading.services.order.core;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.autotrading.command.v1.BrokerCommandServiceGrpc;
import com.autotrading.command.v1.CommandStatus;
import com.autotrading.command.v1.CreateOrderIntentRequest;
import com.autotrading.command.v1.CreateOrderIntentResponse;
import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.libs.idempotency.ClaimOutcome;
import com.autotrading.libs.idempotency.ClaimResult;
import com.autotrading.libs.idempotency.IdempotencyClaim;
import com.autotrading.libs.idempotency.IdempotencyService;
import com.autotrading.libs.reliability.metrics.ReliabilityMetrics;
import com.autotrading.services.order.db.OrderIntentEntity;
import com.autotrading.services.order.db.OrderIntentRepository;
import com.autotrading.services.order.db.OrderLedgerEntity;
import com.autotrading.services.order.db.OrderLedgerRepository;

public class OrderSafetyEngine {
  private static final Logger log = LoggerFactory.getLogger(OrderSafetyEngine.class);

  private final IdempotencyService idempotencyService;
  private final OrderIntentRepository orderIntentRepository;
  private final OrderLedgerRepository orderLedgerRepository;
  private final Map<String, OrderIntentState> ordersById = new ConcurrentHashMap<>();
  private final Map<String, String> orderIdByKey = new ConcurrentHashMap<>();
  private final List<String> alertEvents = new ArrayList<>();
  private final ReliabilityMetrics metrics;
  private final Clock clock;
  private volatile TradingMode tradingMode = TradingMode.NORMAL;

  public OrderSafetyEngine(ReliabilityMetrics metrics, Clock clock,
                           IdempotencyService idempotencyService,
                           OrderIntentRepository orderIntentRepository,
                           OrderLedgerRepository orderLedgerRepository) {
    this.metrics = metrics;
    this.clock = clock;
    this.idempotencyService = idempotencyService;
    this.orderIntentRepository = orderIntentRepository;
    this.orderLedgerRepository = orderLedgerRepository;
  }

  public CreateOrderIntentResponse createOrderIntent(CreateOrderIntentRequest request,
                                                     BrokerCommandServiceGrpc.BrokerCommandServiceBlockingStub brokerStub) {
    if (request.getDecision() == Decision.DECISION_DENY) {
      return CreateOrderIntentResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_REJECTED)
          .addReasons("risk denied")
          .build();
    }

    if (tradingMode == TradingMode.FROZEN) {
      return CreateOrderIntentResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_REJECTED)
          .addReasons("trading mode frozen")
          .build();
    }

    String key = request.getRequestContext().getIdempotencyKey();
    String payloadHash = request.getAgentId() + ":" + request.getSignalId() + ":" + request.getQty();
    ClaimResult claim = idempotencyService.claim(new IdempotencyClaim(key, payloadHash, Instant.now(clock)));

    if (claim.outcome() == ClaimOutcome.CONFLICT) {
      return CreateOrderIntentResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_REJECTED)
          .addReasons("idempotency conflict")
          .build();
    }

    if (claim.outcome() == ClaimOutcome.REPLAY) {
      String existingOrderIntentId = orderIdByKey.get(key);
      return CreateOrderIntentResponse.newBuilder()
          .setTraceId(request.getRequestContext().getTraceId())
          .setStatus(CommandStatus.COMMAND_STATUS_DUPLICATE)
          .setOrderIntentId(existingOrderIntentId == null ? "" : existingOrderIntentId)
          .build();
    }

    String orderIntentId = "ord-" + UUID.randomUUID();
    Instant now = Instant.now(clock);
    Instant deadline = now.plusMillis(60_000);

    SubmitOrderRequest submit = SubmitOrderRequest.newBuilder()
        .setRequestContext(request.getRequestContext())
        .setAgentId(request.getAgentId())
        .setInstrumentId(request.getInstrumentId())
        .setOrderIntentId(orderIntentId)
        .setSide(request.getSide())
        .setQty(request.getQty())
        .setOrderType(request.getOrderType())
        .setTimeInForce(request.getTimeInForce())
        .setSubmissionDeadlineMs(60_000)
        .build();

    var submitResponse = brokerStub.submitOrder(submit);

    OrderIntentState state = new OrderIntentState(
        orderIntentId,
        key,
        now,
        deadline,
        false,
        OrderLifecycleState.SUBMIT_REQUESTED,
        1,
        submitResponse.getBrokerSubmitId());

    ordersById.put(orderIntentId, state);
    orderIdByKey.put(key, orderIntentId);
    idempotencyService.markCompleted(key, orderIntentId);

    // Persist to DB (best-effort, non-blocking to safety engine in-memory state)
    try {
      orderIntentRepository.save(new OrderIntentEntity(
          orderIntentId,
          request.getSignalId().isBlank() ? null : request.getSignalId(),
          request.getAgentId(),
          request.getInstrumentId().isBlank() ? null : request.getInstrumentId(),
          key,
          request.getSide(),
          (int) request.getQty(),
          request.getOrderType(),
          request.getTimeInForce(),
          deadline,
          now));
      orderLedgerRepository.save(new OrderLedgerEntity(
          orderIntentId, "SUBMIT_REQUESTED", 1L, deadline, now, now));
      log.info("order-service persisted orderIntentId={} agentId={}", orderIntentId, request.getAgentId());
    } catch (Exception dbEx) {
      log.warn("order-service DB persist failed orderIntentId={} cause={}", orderIntentId, dbEx.getMessage(), dbEx);
    }

    return CreateOrderIntentResponse.newBuilder()
        .setTraceId(request.getRequestContext().getTraceId())
        .setStatus(CommandStatus.COMMAND_STATUS_ACCEPTED)
        .setOrderIntentId(orderIntentId)
        .build();
  }

  public void onBrokerStatus(String orderIntentId) {
    OrderIntentState current = ordersById.get(orderIntentId);
    if (current == null) {
      return;
    }
    ordersById.put(orderIntentId, current.withStatusObserved(OrderLifecycleState.SUBMITTED_ACKED, Instant.now(clock)));
  }

  public int runTimeoutWatchdog(Instant now) {
    int timeouts = 0;
    for (Map.Entry<String, OrderIntentState> entry : ordersById.entrySet()) {
      OrderIntentState order = entry.getValue();
      if (!order.firstStatusObserved()
          && now.isAfter(order.submissionDeadlineUtc())
          && order.lifecycleState() != OrderLifecycleState.UNKNOWN_PENDING_RECON) {
        ordersById.put(entry.getKey(), order.withTimeoutFreeze());
        tradingMode = TradingMode.FROZEN;
        alertEvents.add("system.alerts.v1:CRITICAL:status_timeout_60s:" + entry.getKey());
        metrics.incrementFirstStatusTimeoutCount();
        timeouts++;
      }
    }
    return timeouts;
  }

  public TradingMode currentTradingMode() {
    return tradingMode;
  }

  public OrderIntentState getOrder(String orderIntentId) {
    return ordersById.get(orderIntentId);
  }

  public List<String> alertEvents() {
    return List.copyOf(alertEvents);
  }

  public synchronized void resetForSmoke() {
    ordersById.clear();
    orderIdByKey.clear();
    alertEvents.clear();
    tradingMode = TradingMode.NORMAL;
  }
}
