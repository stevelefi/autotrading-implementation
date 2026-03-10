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
            .setClientEventId("id-1")
            .setPrincipalId("svc-agent")
            .build())
        .setReason("OPA_TIMEOUT")
        .build());

    assertThat(result.decision().name()).isEqualTo("DECISION_DENY");
    assertThat(result.failureMode().name()).isEqualTo("FAILURE_MODE_OPA_TIMEOUT");
  }

  @Test
  void policyEngineHandlesUnavailableSchemaAndAllowPaths() {
    SimplePolicyEngine engine = new SimplePolicyEngine();

    var unavailable = engine.evaluate(EvaluateSignalRequest.newBuilder().setReason("OPA_UNAVAILABLE").build());
    var schemaError = engine.evaluate(EvaluateSignalRequest.newBuilder().setReason("OPA_SCHEMA_ERROR").build());
    var allowed = engine.evaluate(EvaluateSignalRequest.newBuilder().setReason("strategy-signal").build());

    assertThat(unavailable.decision().name()).isEqualTo("DECISION_DENY");
    assertThat(unavailable.failureMode().name()).isEqualTo("FAILURE_MODE_OPA_UNAVAILABLE");
    assertThat(schemaError.decision().name()).isEqualTo("DECISION_DENY");
    assertThat(schemaError.failureMode().name()).isEqualTo("FAILURE_MODE_OPA_SCHEMA_ERROR");
    assertThat(allowed.decision().name()).isEqualTo("DECISION_ALLOW");
    assertThat(allowed.failureMode().name()).isEqualTo("FAILURE_MODE_NONE");
    assertThat(allowed.matchedRuleIds()).contains("SESSION_WINDOW", "MAX_NET_POSITION");
  }
}
