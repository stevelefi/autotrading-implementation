package com.autotrading.services.risk.core;

import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.EvaluateSignalRequest;
import com.autotrading.command.v1.FailureMode;
import java.util.List;

public class SimplePolicyEngine {

  public PolicyEvaluationResult evaluate(EvaluateSignalRequest request) {
    String reason = request.getReason();

    if ("OPA_TIMEOUT".equalsIgnoreCase(reason)) {
      return new PolicyEvaluationResult(
          Decision.DECISION_DENY,
          FailureMode.FAILURE_MODE_OPA_TIMEOUT,
          List.of(),
          List.of("OPA_TIMEOUT"),
          "2026.03.06-1",
          "prod-default");
    }

    if ("OPA_UNAVAILABLE".equalsIgnoreCase(reason)) {
      return new PolicyEvaluationResult(
          Decision.DECISION_DENY,
          FailureMode.FAILURE_MODE_OPA_UNAVAILABLE,
          List.of(),
          List.of("OPA_UNAVAILABLE"),
          "2026.03.06-1",
          "prod-default");
    }

    if ("OPA_SCHEMA_ERROR".equalsIgnoreCase(reason)) {
      return new PolicyEvaluationResult(
          Decision.DECISION_DENY,
          FailureMode.FAILURE_MODE_OPA_SCHEMA_ERROR,
          List.of(),
          List.of("OPA_SCHEMA_ERROR"),
          "2026.03.06-1",
          "prod-default");
    }

    return new PolicyEvaluationResult(
        Decision.DECISION_ALLOW,
        FailureMode.FAILURE_MODE_NONE,
        List.of("SESSION_WINDOW", "MAX_NET_POSITION"),
        List.of(),
        "2026.03.06-1",
        "prod-default");
  }
}
