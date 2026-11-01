package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_monitoring_execution")
public class InvestorMonitoringExecution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id")
    private Investor investor;

    @Column(name = "execution_trigger", nullable = false, length = 32)
    private String executionTrigger = "SCHEDULED"; // SCHEDULED, INVESTOR_INITIATED

    @Column(name = "execution_status", nullable = false, length = 32)
    private String executionStatus = "PENDING"; // PENDING, RUNNING, COMPLETED, PARTIALLY_COMPLETED, FAILED, SKIPPED

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "knowledge_cutoff")
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "funds_evaluated_count", nullable = false)
    private Integer fundsEvaluatedCount = 0;

    @Column(name = "portfolio_scopes_evaluated_count", nullable = false)
    private Integer portfolioScopesEvaluatedCount = 0;

    @Column(name = "conditions_evaluated_count", nullable = false)
    private Integer conditionsEvaluatedCount = 0;

    @Column(name = "events_generated_count", nullable = false)
    private Integer eventsGeneratedCount = 0;

    @Column(name = "events_deduplicated_count", nullable = false)
    private Integer eventsDeduplicatedCount = 0;

    @Column(name = "unavailable_evidence_count", nullable = false)
    private Integer unavailableEvidenceCount = 0;

    @Column(name = "stale_source_count", nullable = false)
    private Integer staleSourceCount = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "summary_notes", columnDefinition = "text")
    private String summaryNotes;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "withholding_reasons_json", columnDefinition = "text")
    private String withholdingReasonsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorMonitoringExecution() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (startedAt == null) startedAt = ZonedDateTime.now();
        if (fundsEvaluatedCount == null) fundsEvaluatedCount = 0;
        if (portfolioScopesEvaluatedCount == null) portfolioScopesEvaluatedCount = 0;
        if (conditionsEvaluatedCount == null) conditionsEvaluatedCount = 0;
        if (eventsGeneratedCount == null) eventsGeneratedCount = 0;
        if (eventsDeduplicatedCount == null) eventsDeduplicatedCount = 0;
        if (unavailableEvidenceCount == null) unavailableEvidenceCount = 0;
        if (staleSourceCount == null) staleSourceCount = 0;
        if (executionTrigger == null) executionTrigger = "SCHEDULED";
        if (executionStatus == null) executionStatus = "PENDING";
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getWithholdingReasonsJson() { return withholdingReasonsJson; }
    public void setWithholdingReasonsJson(String withholdingReasonsJson) { this.withholdingReasonsJson = withholdingReasonsJson; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getExecutionTrigger() { return executionTrigger; }
    public void setExecutionTrigger(String executionTrigger) { this.executionTrigger = executionTrigger; }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }

    public ZonedDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(ZonedDateTime startedAt) { this.startedAt = startedAt; }

    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public Integer getFundsEvaluatedCount() { return fundsEvaluatedCount; }
    public void setFundsEvaluatedCount(Integer fundsEvaluatedCount) { this.fundsEvaluatedCount = fundsEvaluatedCount; }

    public Integer getPortfolioScopesEvaluatedCount() { return portfolioScopesEvaluatedCount; }
    public void setPortfolioScopesEvaluatedCount(Integer portfolioScopesEvaluatedCount) { this.portfolioScopesEvaluatedCount = portfolioScopesEvaluatedCount; }

    public Integer getConditionsEvaluatedCount() { return conditionsEvaluatedCount; }
    public void setConditionsEvaluatedCount(Integer conditionsEvaluatedCount) { this.conditionsEvaluatedCount = conditionsEvaluatedCount; }

    public Integer getEventsGeneratedCount() { return eventsGeneratedCount; }
    public void setEventsGeneratedCount(Integer eventsGeneratedCount) { this.eventsGeneratedCount = eventsGeneratedCount; }

    public Integer getEventsDeduplicatedCount() { return eventsDeduplicatedCount; }
    public void setEventsDeduplicatedCount(Integer eventsDeduplicatedCount) { this.eventsDeduplicatedCount = eventsDeduplicatedCount; }

    public Integer getUnavailableEvidenceCount() { return unavailableEvidenceCount; }
    public void setUnavailableEvidenceCount(Integer unavailableEvidenceCount) { this.unavailableEvidenceCount = unavailableEvidenceCount; }

    public Integer getStaleSourceCount() { return staleSourceCount; }
    public void setStaleSourceCount(Integer staleSourceCount) { this.staleSourceCount = staleSourceCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getSummaryNotes() { return summaryNotes; }
    public void setSummaryNotes(String summaryNotes) { this.summaryNotes = summaryNotes; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorMonitoringExecution that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

