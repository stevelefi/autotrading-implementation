package com.autotrading.services.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.RequestContext;
import com.autotrading.services.risk.core.SimplePolicyEngine;
import org.junit.jupiter.api.Test;

class SimplePolicyEngineTest {

  @Test
  void opaTimeoutReasonFailsClosed() {
    SimplePolicyEngine engine = new SimplePolicyEngine();

    var result = engine.evaluate(EvaluateSignalRequest.newBuilder()
        .setRequestContext(RequestContext.newBuilder()
            .setTraceId("trc-1")
            .setRequestId("req-1")
            .setIdempotencyKey("id-1")
            .setPrincipalId("svc-agent")
            .build())
        .setReason("OPA_TIMEOUT")
        .build());

    assertThat(result.decision().name()).isEqualTo("DECISION_DENY");
    assertThat(result.failureMode().name()).isEqualTo("FAILURE_MODE_OPA_TIMEOUT");
  }
}
