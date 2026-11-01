package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_monitoring_condition_history")
public class InvestorMonitoringConditionHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_id", nullable = false)
    private InvestorMonitoringCondition condition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private InvestorMonitoringExecution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "evaluated_at", nullable = false)
    private ZonedDateTime evaluatedAt;

    @Column(name = "observation_cutoff")
    private LocalDate observationCutoff;

    @Column(name = "knowledge_cutoff")
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "evaluation_outcome", nullable = false, length = 64)
    private String evaluationOutcome; // TRIGGERED, NOT_TRIGGERED, INSUFFICIENT_EVIDENCE, STALE_SOURCE_DATA, MISSING_SOURCE_OBSERVATIONS, INCOMPATIBLE_WINDOW, EVALUATION_FAILURE

    @Column(name = "trigger_status", nullable = false, length = 32)
    private String triggerStatus; // TRIGGERED, CLEAR, WITHHELD, FAILED

    @Column(name = "withholding_reason", length = 64)
    private String withholdingReason;

    @Column(name = "evaluated_value")
    private String evaluatedValue;

    @Column(name = "threshold_value", length = 64)
    private String thresholdValue;

    @Column(name = "source_context")
    private String sourceContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private InvestorMonitoringEvent event;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorMonitoringConditionHistory() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (evaluatedAt == null) evaluatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorMonitoringCondition getCondition() { return condition; }
    public void setCondition(InvestorMonitoringCondition condition) { this.condition = condition; }

    public InvestorMonitoringExecution getExecution() { return execution; }
    public void setExecution(InvestorMonitoringExecution execution) { this.execution = execution; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public ZonedDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(ZonedDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public LocalDate getObservationCutoff() { return observationCutoff; }
    public void setObservationCutoff(LocalDate observationCutoff) { this.observationCutoff = observationCutoff; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public String getEvaluationOutcome() { return evaluationOutcome; }
    public void setEvaluationOutcome(String evaluationOutcome) { this.evaluationOutcome = evaluationOutcome; }

    public String getTriggerStatus() { return triggerStatus; }
    public void setTriggerStatus(String triggerStatus) { this.triggerStatus = triggerStatus; }

    public String getWithholdingReason() { return withholdingReason; }
    public void setWithholdingReason(String withholdingReason) { this.withholdingReason = withholdingReason; }

    public String getEvaluatedValue() { return evaluatedValue; }
    public void setEvaluatedValue(String evaluatedValue) { this.evaluatedValue = evaluatedValue; }

    public String getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(String thresholdValue) { this.thresholdValue = thresholdValue; }

    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }

    public InvestorMonitoringEvent getEvent() { return event; }
    public void setEvent(InvestorMonitoringEvent event) { this.event = event; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorMonitoringConditionHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

