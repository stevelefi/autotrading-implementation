package com.autotrading.services.ibkr.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ExecutionEntityTest {

  @Test
  void constructsAndExposesAllFields() {
    var fillTs = Instant.parse("2026-01-01T10:00:00Z");
    var createdAt = Instant.parse("2026-01-01T10:00:01Z");

    var entity = new ExecutionEntity(
        "exec-abc", "intent-123", "order-456",
        "agent-007", "AAPL", "BUY",
        100, new BigDecimal("150.25"), new BigDecimal("1.50"),
        fillTs, createdAt);

    assertThat(entity.getExecId()).isEqualTo("exec-abc");
    assertThat(entity.getOrderIntentId()).isEqualTo("intent-123");
    assertThat(entity.getBrokerOrderId()).isEqualTo("order-456");
    assertThat(entity.getAgentId()).isEqualTo("agent-007");
    assertThat(entity.getInstrumentId()).isEqualTo("AAPL");
    assertThat(entity.getSide()).isEqualTo("BUY");
    assertThat(entity.getFillQty()).isEqualTo(100);
    assertThat(entity.getFillPrice()).isEqualByComparingTo("150.25");
    assertThat(entity.getCommission()).isEqualByComparingTo("1.50");
    assertThat(entity.getFillTs()).isEqualTo(fillTs);
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void nullableFieldsAccepted() {
    var now = Instant.now();
    var entity = new ExecutionEntity(
        "exec-1", "intent-1", null, null, null, "SELL",
        50, new BigDecimal("200.00"), null, now, now);

    assertThat(entity.getBrokerOrderId()).isNull();
    assertThat(entity.getAgentId()).isNull();
    assertThat(entity.getInstrumentId()).isNull();
    assertThat(entity.getCommission()).isNull();
  }
}
