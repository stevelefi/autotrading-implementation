package com.autotrading.services.risk.core;

import com.autotrading.command.v1.Decision;
import com.autotrading.command.v1.FailureMode;
import java.util.List;

public record PolicyEvaluationResult(
    Decision decision,
    FailureMode failureMode,
    List<String> matchedRuleIds,
    List<String> denyReasons,
    String policyVersion,
    String policyRuleSet
) {
}
