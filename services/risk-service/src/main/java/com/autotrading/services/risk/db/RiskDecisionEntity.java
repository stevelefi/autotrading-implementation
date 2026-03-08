package com.autotrading.services.risk.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "risk_decisions")
public class RiskDecisionEntity {

  @Id
  @Column(name = "risk_decision_id")
  private String riskDecisionId;

  @Column(name = "signal_id", nullable = false)
  private String signalId;

  @Column(name = "trace_id", nullable = false)
  private String traceId;

  @Column(name = "decision", nullable = false)
  private String decision;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "deny_reasons_json", nullable = false, columnDefinition = "jsonb")
  private String denyReasonsJson;

  @Column(name = "policy_version", nullable = false)
  private String policyVersion;

  @Column(name = "policy_rule_set", nullable = false)
  private String policyRuleSet;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matched_rule_ids_json", nullable = false, columnDefinition = "jsonb")
  private String matchedRuleIdsJson;

  @Column(name = "failure_mode", nullable = false)
  private String failureMode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RiskDecisionEntity() {}

  public RiskDecisionEntity(String riskDecisionId, String signalId, String traceId,
                             String decision, String denyReasonsJson, String policyVersion,
                             String policyRuleSet, String matchedRuleIdsJson, String failureMode,
                             Instant createdAt) {
    this.riskDecisionId = riskDecisionId;
    this.signalId = signalId;
    this.traceId = traceId;
    this.decision = decision;
    this.denyReasonsJson = denyReasonsJson;
    this.policyVersion = policyVersion;
    this.policyRuleSet = policyRuleSet;
    this.matchedRuleIdsJson = matchedRuleIdsJson;
    this.failureMode = failureMode;
    this.createdAt = createdAt;
  }

  public String getRiskDecisionId() { return riskDecisionId; }
  public String getSignalId() { return signalId; }
  public String getTraceId() { return traceId; }
  public String getDecision() { return decision; }
  public String getDenyReasonsJson() { return denyReasonsJson; }
  public String getPolicyVersion() { return policyVersion; }
  public String getPolicyRuleSet() { return policyRuleSet; }
  public String getMatchedRuleIdsJson() { return matchedRuleIdsJson; }
  public String getFailureMode() { return failureMode; }
  public Instant getCreatedAt() { return createdAt; }
}
