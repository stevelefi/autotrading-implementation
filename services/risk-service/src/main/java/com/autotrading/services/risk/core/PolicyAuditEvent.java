package com.autotrading.services.risk.core;

import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.FailureMode;
import java.time.Instant;
import java.util.List;

public record PolicyAuditEvent(
    String traceId,
    String agentId,
    String signalId,
    Decision decision,
    String policyVersion,
    String policyRuleSet,
    List<String> matchedRuleIds,
    List<String> denyReasons,
    FailureMode failureMode,
    long latencyMs,
    Instant occurredAtUtc
) {
}
