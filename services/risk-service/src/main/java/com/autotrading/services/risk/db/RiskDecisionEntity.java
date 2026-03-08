package com.autotrading.services.risk.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("risk_decisions")
public class RiskDecisionEntity implements Persistable<String> {

  @Id
  @Column("risk_decision_id")
  private String riskDecisionId;

  @Column("signal_id")
  private String signalId;

  @Column("trace_id")
  private String traceId;

  @Column("decision")
  private String decision;

  @Column("deny_reasons_json")
  private String denyReasonsJson;

  @Column("policy_version")
  private String policyVersion;

  @Column("policy_rule_set")
  private String policyRuleSet;

  @Column("matched_rule_ids_json")
  private String matchedRuleIdsJson;

  @Column("failure_mode")
  private String failureMode;

  @Column("created_at")
  private Instant createdAt;

  @Transient private boolean isNewEntity;

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
    this.isNewEntity = true;
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

  @Override public String getId() { return riskDecisionId; }
  @Override public boolean isNew() { return isNewEntity; }
}
