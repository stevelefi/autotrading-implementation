package com.autotrading.services.ibkr.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BrokerOrderEntityTest {

  private BrokerOrderEntity build(String status) {
    var now = Instant.now();
    return new BrokerOrderEntity(
        "bo-1", "intent-1", "ref-1", "perm-1",
        "agent-1", "AAPL", "BUY", 50,
        "MARKET", status, now, now);
  }

  @Test
  void constructsAndExposesAllFields() {
    var submitted = Instant.parse("2026-01-01T09:00:00Z");
    var updated = Instant.parse("2026-01-01T09:00:01Z");

    var entity = new BrokerOrderEntity(
        "bo-abc", "intent-xyz", "ref-001", "perm-999",
        "agent-007", "TSLA", "SELL", 25,
        "LIMIT", "SUBMITTED", submitted, updated);

    assertThat(entity.getBrokerOrderId()).isEqualTo("bo-abc");
    assertThat(entity.getOrderIntentId()).isEqualTo("intent-xyz");
    assertThat(entity.getOrderRef()).isEqualTo("ref-001");
    assertThat(entity.getPermId()).isEqualTo("perm-999");
    assertThat(entity.getAgentId()).isEqualTo("agent-007");
    assertThat(entity.getInstrumentId()).isEqualTo("TSLA");
    assertThat(entity.getSide()).isEqualTo("SELL");
    assertThat(entity.getQty()).isEqualTo(25);
    assertThat(entity.getOrderType()).isEqualTo("LIMIT");
    assertThat(entity.getStatus()).isEqualTo("SUBMITTED");
    assertThat(entity.getSubmittedAt()).isEqualTo(submitted);
    assertThat(entity.getUpdatedAt()).isEqualTo(updated);
  }

  @Test
  void setStatusMutatesStatus() {
    var entity = build("SUBMITTED");
    entity.setStatus("FILLED");
    assertThat(entity.getStatus()).isEqualTo("FILLED");
  }

  @Test
  void setUpdatedAtMutatesTimestamp() {
    var entity = build("SUBMITTED");
    var newTs = Instant.now().plusSeconds(60);
    entity.setUpdatedAt(newTs);
    assertThat(entity.getUpdatedAt()).isEqualTo(newTs);
  }

  @Test
  void nullablePermIdAccepted() {
    var now = Instant.now();
    var entity = new BrokerOrderEntity(
        "bo-1", "intent-1", "ref-1", null,
        "agent-1", "AAPL", "BUY", 10,
        "MARKET", "PENDING", now, now);
    assertThat(entity.getPermId()).isNull();
  }
}
