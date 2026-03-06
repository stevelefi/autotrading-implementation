package com.autotrading.libs.commonenvelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

  private RequestContext ctx() {
    return new RequestContext("trace-1", "req-1", "idem-1", "principal-1", Instant.now());
  }

  @Test
  void constructsAndExposesAllFields() {
    var now = Instant.now();
    var env = new EventEnvelope<>("event-id-1", "ORDER_SUBMITTED", 1, now, ctx(), "agent-1", "AAPL", "payload-value");

    assertThat(env.eventId()).isEqualTo("event-id-1");
    assertThat(env.eventType()).isEqualTo("ORDER_SUBMITTED");
    assertThat(env.eventVersion()).isEqualTo(1);
    assertThat(env.occurredAt()).isEqualTo(now);
    assertThat(env.agentId()).isEqualTo("agent-1");
    assertThat(env.instrumentId()).isEqualTo("AAPL");
    assertThat(env.payload()).isEqualTo("payload-value");
    assertThat(env.context()).isNotNull();
  }

  @Test
  void rejectsNullEventId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>(null, "TYPE", 1, Instant.now(), ctx(), "a", "i", "p"))
        .withMessageContaining("eventId");
  }

  @Test
  void rejectsNullEventType() {
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>("id", null, 1, Instant.now(), ctx(), "a", "i", "p"))
        .withMessageContaining("eventType");
  }

  @Test
  void rejectsNullOccurredAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>("id", "TYPE", 1, null, ctx(), "a", "i", "p"))
        .withMessageContaining("occurredAt");
  }

  @Test
  void rejectsNullContext() {
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>("id", "TYPE", 1, Instant.now(), null, "a", "i", "p"))
        .withMessageContaining("context");
  }

  @Test
  void rejectsNullPayload() {
    assertThatNullPointerException()
        .isThrownBy(() -> new EventEnvelope<>("id", "TYPE", 1, Instant.now(), ctx(), "a", "i", null))
        .withMessageContaining("payload");
  }

  @Test
  void nullableFieldsAreAllowed() {
    // agentId and instrumentId are nullable (no null-check in compact constructor)
    var env = new EventEnvelope<>("id", "TYPE", 1, Instant.now(), ctx(), null, null, "payload");
    assertThat(env.agentId()).isNull();
    assertThat(env.instrumentId()).isNull();
  }
}
