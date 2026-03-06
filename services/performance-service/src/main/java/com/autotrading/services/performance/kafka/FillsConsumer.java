package com.autotrading.services.performance.kafka;

import com.autotrading.libs.reliability.inbox.ConsumerDeduper;
import com.autotrading.services.performance.core.PerformanceProjectionService;
import com.autotrading.services.performance.db.PnlSnapshotEntity;
import com.autotrading.services.performance.db.PnlSnapshotRepository;
import com.autotrading.services.performance.db.PositionEntity;
import com.autotrading.services.performance.db.PositionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes fill events from {@code fills.executed.v1} and updates positions/PnL snapshots.
 */
@Component
public class FillsConsumer {

  private static final Logger log = LoggerFactory.getLogger(FillsConsumer.class);
  private static final String CONSUMER_NAME = "performance";

  private final PerformanceProjectionService projectionService;
  private final PositionRepository positionRepository;
  private final PnlSnapshotRepository pnlSnapshotRepository;
  private final ConsumerDeduper consumerDeduper;
  private final ObjectMapper objectMapper;

  public FillsConsumer(PerformanceProjectionService projectionService,
                       PositionRepository positionRepository,
                       PnlSnapshotRepository pnlSnapshotRepository,
                       ConsumerDeduper consumerDeduper,
                       ObjectMapper objectMapper) {
    this.projectionService = projectionService;
    this.positionRepository = positionRepository;
    this.pnlSnapshotRepository = pnlSnapshotRepository;
    this.consumerDeduper = consumerDeduper;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "fills.executed.v1", groupId = "performance-service")
  @Transactional
  public void onFill(ConsumerRecord<String, String> record) {
    try {
      processRecord(record);
    } catch (Exception e) {
      log.error("performance-service failed to process fill offset={} cause={}", record.offset(), e.getMessage(), e);
      throw new RuntimeException("performance fills consumer failure", e);
    } finally {
      MDC.clear();
    }
  }

  private void processRecord(ConsumerRecord<String, String> record) throws Exception {
    JsonNode root = objectMapper.readTree(record.value());

    String execId = root.path("execId").asText();
    String agentId = root.path("agentId").asText(null);
    String instrumentId = root.path("instrumentId").asText("eq_tqqq");
    int fillQty = root.path("fillQty").asInt(0);
    BigDecimal fillPrice = new BigDecimal(root.path("fillPrice").asText("0"));

    // Set MDC for structured logging
    MDC.put("request_id", execId);
    if (agentId != null) MDC.put("agent_id", agentId);
    MDC.put("instrument_id", instrumentId);

    final String finalAgentId = agentId != null ? agentId : "anonymous";
    final BigDecimal finalFillPrice = fillPrice;
    final int finalFillQty = fillQty;

    boolean processed = consumerDeduper.runOnce(CONSUMER_NAME, execId, () -> {
      try {
        boolean applied = projectionService.applyFill(finalAgentId, instrumentId, execId, finalFillQty, finalFillPrice);
        if (!applied) {
          return;
        }

        PerformanceProjectionService.PositionSnapshot snapshot = projectionService.getPosition(finalAgentId, instrumentId);
        Instant now = Instant.now();

        // Upsert position in DB
        PositionEntity.PositionId posId = new PositionEntity.PositionId(finalAgentId, instrumentId);
        PositionEntity posEntity = positionRepository.findById(posId)
            .orElseGet(() -> new PositionEntity(finalAgentId, instrumentId, 0, BigDecimal.ZERO, BigDecimal.ZERO, now));
        posEntity.setQty(snapshot.netQty());
        posEntity.setAvgCost(snapshot.avgCost());
        posEntity.setUpdatedAt(now);
        positionRepository.save(posEntity);

        // Persist PnL snapshot
        pnlSnapshotRepository.save(new PnlSnapshotEntity(
            "pnl-" + UUID.randomUUID(),
            finalAgentId,
            instrumentId,
            now,
            BigDecimal.ZERO,   // unrealized_pnl (market data not available)
            posEntity.getRealizedPnl(),
            posEntity.getRealizedPnl(),
            snapshot.netQty(),
            snapshot.avgCost(),
            now));

        log.info("performance updated position agentId={} instrumentId={} qty={} avgCost={}",
            finalAgentId, instrumentId, snapshot.netQty(), snapshot.avgCost());
      } catch (RuntimeException re) {
        throw re;
      } catch (Exception e) {
        throw new RuntimeException("performance fill processing failed execId=" + execId, e);
      }
    });

    if (!processed) {
      log.debug("performance duplicate suppressed execId={}", execId);
    }
  }
}
