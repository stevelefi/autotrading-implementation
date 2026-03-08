package com.autotrading.services.risk.db;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("policy_decision_log")
public class PolicyDecisionLogEntity implements Persistable<String> {

  @Id
  @Column("log_id")
  private String logId;

  @Column("risk_decision_id")
  private String riskDecisionId;

  @Column("trace_id")
  private String traceId;

  @Column("agent_id")
  private String agentId;

  @Column("signal_id")
  private String signalId;

  @Column("decision")
  private String decision;

  @Column("policy_version")
  private String policyVersion;

  @Column("policy_rule_set")
  private String policyRuleSet;

  @Column("latency_ms")
  private Long latencyMs;

  @Column("created_at")
  private Instant createdAt;

  @Transient private boolean isNewEntity;

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
    this.isNewEntity = true;
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

  @Override public String getId() { return logId; }
  @Override public boolean isNew() { return isNewEntity; }
}
