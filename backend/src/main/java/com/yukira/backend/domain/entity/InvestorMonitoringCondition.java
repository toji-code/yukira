package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_monitoring_condition")
public class InvestorMonitoringCondition implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id")
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "target_scope", nullable = false, length = 32)
    private String targetScope = "SCHEME_OPTION"; // SCHEME_OPTION, PORTFOLIO

    @Column(name = "condition_name", nullable = false)
    private String conditionName;

    @Column(name = "evidence_field", nullable = false, length = 64)
    private String evidenceField;

    @Column(name = "operator", nullable = false, length = 32)
    private String operator; // GT, GTE, LT, LTE, ABS_CHANGE_GT, DATA_UNAVAILABLE, EVIDENCE_STALE

    @Column(name = "threshold_value", length = 64)
    private String thresholdValue;

    @Column(name = "threshold_numeric", precision = 19, scale = 4)
    private BigDecimal thresholdNumeric;

    @Column(name = "threshold_unit", length = 32)
    private String thresholdUnit;

    @Column(name = "threshold_classification", length = 32)
    private String thresholdClassification = "INVESTOR_DEFINED_THRESHOLD"; // INVESTOR_DEFINED_THRESHOLD, SYSTEM_CANDIDATE_THRESHOLD

    @Column(name = "time_window", length = 32)
    private String timeWindow; // 1Y, 3Y, 5Y, MONTHLY, POINT_IN_TIME

    @Column(name = "associated_thesis_key", length = 128)
    private String associatedThesisKey;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "stress_finding_id", length = 100)
    private String stressFindingId;

    @Column(name = "thesis_reconsideration_guidance", columnDefinition = "text")
    private String thesisReconsiderationGuidance;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Column(name = "condition_status", nullable = false, length = 32)
    private String conditionStatus = "ACTIVE"; // ACTIVE, PAUSED, RESOLVED, DISMISSED

    @Column(name = "originating_finding_id", length = 64)
    private String originatingFindingId;

    @Column(name = "evaluation_state", nullable = false, length = 32)
    private String evaluationState = "NOT_EVALUATED"; // TRIGGERED, CLEAR, NOT_EVALUATED, DATA_UNAVAILABLE, REQUIRES_REVIEW

    @Column(name = "evidence_state", length = 32)
    private String evidenceState = "AVAILABLE"; // AVAILABLE, STALE, MISSING, INSUFFICIENT_HISTORY, DATA_QUALITY_LIMITED, UNRESOLVED, METHODOLOGY_INCOMPATIBLE

    @Column(name = "last_evaluated_value")
    private String lastEvaluatedValue;

    @Column(name = "last_evaluated_at")
    private ZonedDateTime lastEvaluatedAt;

    @Column(name = "last_observation_date")
    private LocalDate lastObservationDate;

    @Column(name = "trigger_count")
    private Integer triggerCount = 0;

    @Column(name = "last_triggered_at")
    private ZonedDateTime lastTriggeredAt;

    @Column(name = "evaluation_cadence", nullable = false, length = 32)
    private String evaluationCadence = "DAILY"; // DAILY, WEEKLY, MONTHLY

    @Column(name = "next_evaluation_due_at")
    private ZonedDateTime nextEvaluationDueAt;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "last_withholding_reason", length = 64)
    private String lastWithholdingReason;

    @Column(name = "last_outcome", nullable = false, length = 32)
    private String lastOutcome = "NOT_EVALUATED"; // TRIGGERED, NOT_TRIGGERED, INSUFFICIENT_EVIDENCE, STALE_SOURCE_DATA, MISSING_SOURCE_OBSERVATIONS, EVALUATION_FAILURE

    @Column(name = "last_successful_evaluated_at")
    private ZonedDateTime lastSuccessfulEvaluatedAt;

    @Column(name = "last_failed_evaluated_at")
    private ZonedDateTime lastFailedEvaluatedAt;

    @Column(name = "paused_at")
    private ZonedDateTime pausedAt;

    @Column(name = "pause_reason")
    private String pauseReason;

    @Column(name = "resume_at")
    private ZonedDateTime resumeAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public InvestorMonitoringCondition() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (isEnabled == null) isEnabled = true;
        if (conditionStatus == null) conditionStatus = "ACTIVE";
        if (evaluationState == null) evaluationState = "NOT_EVALUATED";
        if (evidenceState == null) evidenceState = "AVAILABLE";
        if (targetScope == null) targetScope = "SCHEME_OPTION";
        if (thresholdClassification == null) thresholdClassification = "INVESTOR_DEFINED_THRESHOLD";
        if (triggerCount == null) triggerCount = 0;
        if (evaluationCadence == null) evaluationCadence = "DAILY";
        if (lastOutcome == null) lastOutcome = "NOT_EVALUATED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }

    public String getConditionName() { return conditionName; }
    public void setConditionName(String conditionName) { this.conditionName = conditionName; }

    public String getEvidenceField() { return evidenceField; }
    public String getMetricCode() { return evidenceField; }
    public void setEvidenceField(String evidenceField) { this.evidenceField = evidenceField; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(String thresholdValue) { this.thresholdValue = thresholdValue; }

    public BigDecimal getThresholdNumeric() { return thresholdNumeric; }
    public void setThresholdNumeric(BigDecimal thresholdNumeric) { this.thresholdNumeric = thresholdNumeric; }

    public String getThresholdUnit() { return thresholdUnit; }
    public void setThresholdUnit(String thresholdUnit) { this.thresholdUnit = thresholdUnit; }

    public String getThresholdClassification() { return thresholdClassification; }
    public void setThresholdClassification(String thresholdClassification) { this.thresholdClassification = thresholdClassification; }

    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }

    public String getAssociatedThesisKey() { return associatedThesisKey; }
    public void setAssociatedThesisKey(String associatedThesisKey) { this.associatedThesisKey = associatedThesisKey; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public String getStressFindingId() { return stressFindingId; }
    public void setStressFindingId(String stressFindingId) { this.stressFindingId = stressFindingId; }

    public String getThesisReconsiderationGuidance() { return thesisReconsiderationGuidance; }
    public String getNotes() { return thesisReconsiderationGuidance; }
    public void setThesisReconsiderationGuidance(String thesisReconsiderationGuidance) { this.thesisReconsiderationGuidance = thesisReconsiderationGuidance; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public String getConditionStatus() { return conditionStatus; }
    public String getStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }

    public String getOriginatingFindingId() { return originatingFindingId; }
    public void setOriginatingFindingId(String originatingFindingId) { this.originatingFindingId = originatingFindingId; }

    public String getEvaluationState() { return evaluationState; }
    public void setEvaluationState(String evaluationState) { this.evaluationState = evaluationState; }

    public String getEvidenceState() { return evidenceState; }
    public void setEvidenceState(String evidenceState) { this.evidenceState = evidenceState; }

    public String getLastEvaluatedValue() { return lastEvaluatedValue; }
    public void setLastEvaluatedValue(String lastEvaluatedValue) { this.lastEvaluatedValue = lastEvaluatedValue; }

    public ZonedDateTime getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(ZonedDateTime lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }

    public LocalDate getLastObservationDate() { return lastObservationDate; }
    public void setLastObservationDate(LocalDate lastObservationDate) { this.lastObservationDate = lastObservationDate; }

    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }

    public ZonedDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(ZonedDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getEvaluationCadence() { return evaluationCadence; }
    public void setEvaluationCadence(String evaluationCadence) { this.evaluationCadence = evaluationCadence; }

    public ZonedDateTime getNextEvaluationDueAt() { return nextEvaluationDueAt; }
    public void setNextEvaluationDueAt(ZonedDateTime nextEvaluationDueAt) { this.nextEvaluationDueAt = nextEvaluationDueAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getLastWithholdingReason() { return lastWithholdingReason; }
    public void setLastWithholdingReason(String lastWithholdingReason) { this.lastWithholdingReason = lastWithholdingReason; }

    public String getLastOutcome() { return lastOutcome; }
    public void setLastOutcome(String lastOutcome) { this.lastOutcome = lastOutcome; }

    public ZonedDateTime getLastSuccessfulEvaluatedAt() { return lastSuccessfulEvaluatedAt; }
    public void setLastSuccessfulEvaluatedAt(ZonedDateTime lastSuccessfulEvaluatedAt) { this.lastSuccessfulEvaluatedAt = lastSuccessfulEvaluatedAt; }

    public ZonedDateTime getLastFailedEvaluatedAt() { return lastFailedEvaluatedAt; }
    public void setLastFailedEvaluatedAt(ZonedDateTime lastFailedEvaluatedAt) { this.lastFailedEvaluatedAt = lastFailedEvaluatedAt; }

    public ZonedDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(ZonedDateTime pausedAt) { this.pausedAt = pausedAt; }

    public String getPauseReason() { return pauseReason; }
    public void setPauseReason(String pauseReason) { this.pauseReason = pauseReason; }

    public ZonedDateTime getResumeAt() { return resumeAt; }
    public void setResumeAt(ZonedDateTime resumeAt) { this.resumeAt = resumeAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorMonitoringCondition that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
