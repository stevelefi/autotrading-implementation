package com.autotrading.services.risk.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "policy_decision_log")
public class PolicyDecisionLogEntity {

  @Id
  @Column(name = "log_id")
  private String logId;

  @Column(name = "risk_decision_id", nullable = false)
  private String riskDecisionId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "agent_id")
  private String agentId;

  @Column(name = "signal_id", nullable = false)
  private String signalId;

  @Column(name = "decision", nullable = false)
  private String decision;

  @Column(name = "policy_version", nullable = false)
  private String policyVersion;

  @Column(name = "policy_rule_set", nullable = false)
  private String policyRuleSet;

  @Column(name = "latency_ms")
  private Long latencyMs;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PolicyDecisionLogEntity() {}

  public PolicyDecisionLogEntity(String logId, String riskDecisionId, String traceId,
                                  String agentId, String signalId, String decision,
                                  String policyVersion, String policyRuleSet,
                                  Long latencyMs, Instant createdAt) {
    this.logId = logId;
    this.riskDecisionId = riskDecisionId;
    this.traceId = traceId;
    this.agentId = agentId;
    this.signalId = signalId;
    this.decision = decision;
    this.policyVersion = policyVersion;
    this.policyRuleSet = policyRuleSet;
    this.latencyMs = latencyMs;
    this.createdAt = createdAt;
  }

  public String getLogId() { return logId; }
  public String getRiskDecisionId() { return riskDecisionId; }
  public String getTraceId() { return traceId; }
  public String getAgentId() { return agentId; }
  public String getSignalId() { return signalId; }
  public String getDecision() { return decision; }
  public String getPolicyVersion() { return policyVersion; }
  public String getPolicyRuleSet() { return policyRuleSet; }
  public Long getLatencyMs() { return latencyMs; }
  public Instant getCreatedAt() { return createdAt; }
}
