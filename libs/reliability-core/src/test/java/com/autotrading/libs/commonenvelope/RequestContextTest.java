package com.autotrading.libs.commonenvelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RequestContextTest {

  @Test
  void constructsAndExposesAllFields() {
    var now = Instant.now();
    var ctx = new RequestContext("trace-1", "req-1", "idem-1", "principal-1", now);

    assertThat(ctx.traceId()).isEqualTo("trace-1");
    assertThat(ctx.requestId()).isEqualTo("req-1");
    assertThat(ctx.clientEventId()).isEqualTo("idem-1");
    assertThat(ctx.principalId()).isEqualTo("principal-1");
    assertThat(ctx.receivedAtUtc()).isEqualTo(now);
  }

  @Test
  void rejectsNullTraceId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequestContext(null, "r", "ik", "p", Instant.now()))
        .withMessageContaining("traceId");
  }

  @Test
  void rejectsNullRequestId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequestContext("t", null, "ik", "p", Instant.now()))
        .withMessageContaining("requestId");
  }

  @Test
  void rejectsNullClientEventId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequestContext("t", "r", null, "p", Instant.now()))
        .withMessageContaining("clientEventId");
  }

  @Test
  void rejectsNullPrincipalId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequestContext("t", "r", "ik", null, Instant.now()))
        .withMessageContaining("principalId");
  }

  @Test
  void rejectsNullReceivedAt() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequestContext("t", "r", "ik", "p", null))
        .withMessageContaining("receivedAtUtc");
  }

  @Test
  void recordEquality() {
    var now = Instant.now();
    var a = new RequestContext("t", "r", "ik", "p", now);
    var b = new RequestContext("t", "r", "ik", "p", now);
    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
  }
}
