package com.autotrading.services.ibkr.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.autotrading.services.ibkr.core.BrokerConnectorEngine;

/**
 * Polls the IBKR Client Portal REST API for live order updates and trade fills.
 *
 * <p>On each cycle (default 5 s):
 * <ol>
 *   <li>Skip entirely if {@link IbkrHealthProbe#isUp()} returns false.</li>
 *   <li>Call {@code GET /v1/api/iserver/account/trades} and call
 *       {@link BrokerConnectorEngine#recordExecution} for any new {@code execution_id}.</li>
 *   <li>Log status changes for monitored orders (order-status polling is informational only;
 *       Kafka publishes for status flow are handled at submit time).</li>
 * </ol>
 *
 * <p>Phase {@code 150} — after {@link IbkrHealthProbe} (phase 100), before gRPC server (phase 200).
 */
public class IbkrOrderPoller implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(IbkrOrderPoller.class);
  private static final int PHASE = 150;

  private final IbkrRestClient restClient;
  private final BrokerConnectorEngine engine;
  private final IbkrHealthProbe healthProbe;
  private final long pollIntervalMs;

  /** Tracks execution IDs already forwarded to the engine for deduplication across polls. */
  private final Set<String> seenExecutionIds = ConcurrentHashMap.newKeySet();

  private ScheduledExecutorService scheduler;

  public IbkrOrderPoller(IbkrRestClient restClient,
                          BrokerConnectorEngine engine,
                          IbkrHealthProbe healthProbe,
                          long pollIntervalMs) {
    this.restClient = restClient;
    this.engine = engine;
    this.healthProbe = healthProbe;
    this.pollIntervalMs = pollIntervalMs;
  }

  // ------------------------------------------------------------------
  // SmartLifecycle
  // ------------------------------------------------------------------

  @Override
  public void start() {
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ibkr-order-poller");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleAtFixedRate(this::runPoll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
    log.info("IbkrOrderPoller started pollIntervalMs={}", pollIntervalMs);
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    log.info("IbkrOrderPoller stopped");
  }

  @Override
  public boolean isRunning() {
    return scheduler != null && !scheduler.isShutdown();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return PHASE;
  }

  // ------------------------------------------------------------------
  // Internal (public for cross-package test access)
  // ------------------------------------------------------------------

  public void runPoll() {
    if (!healthProbe.isUp()) {
      log.debug("IbkrOrderPoller skipping poll — broker not UP (status={})", healthProbe.getStatus());
      return;
    }

    pollTrades();
  }

  private void pollTrades() {
    try {
      List<IbkrRestClient.IbkrTrade> trades = restClient.getTrades();
      for (var trade : trades) {
        String execId = trade.executionId();
        if (execId == null || execId.isBlank()) {
          continue;
        }
        if (!seenExecutionIds.add(execId)) {
          continue; // already forwarded
        }

        BigDecimal fillPrice = trade.price() != null ? BigDecimal.valueOf(trade.price()) : BigDecimal.ZERO;
        BigDecimal fillQty = trade.size() != null ? BigDecimal.valueOf(trade.size()) : BigDecimal.ONE;
        BigDecimal commission = trade.commission() != null ? BigDecimal.valueOf(trade.commission()) : BigDecimal.ZERO;
        Instant fillTs = trade.tradeTimeMs() != null
            ? Instant.ofEpochMilli(trade.tradeTimeMs())
            : Instant.now();

        // orderId from IBKR is the broker's numeric ID; we use it as the brokerOrderId reference.
        String brokerOrderId = trade.orderId() != null ? trade.orderId() : "unknown";
        String symbol = trade.symbol() != null ? trade.symbol() : "";
        String side = trade.side() != null ? trade.side() : "UNKNOWN";

        // We don't have the original orderIntentId at this layer — use execId-derived placeholder.
        // The engine's recordExecution deduplicates on execId, so the key here is uniqueness.
        String orderIntentId = "ibkr-fill:" + brokerOrderId;

        log.info("IbkrOrderPoller new fill execId={} brokerOrderId={} qty={} price={}",
            execId, brokerOrderId, fillQty, fillPrice);

        engine.recordExecution(
            execId,
            orderIntentId,
            brokerOrderId,
            "ibkr", // agentId not available at this layer; filled by the fill event consumer
            symbol,
            side,
            fillQty,
            fillPrice,
            commission,
            fillTs);
      }
    } catch (Exception e) {
      log.warn("IbkrOrderPoller trade poll failed: {}", e.getMessage());
    }
  }
}
