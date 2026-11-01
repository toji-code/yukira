package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "investor_monitoring_event")
public class InvestorMonitoringEvent implements Serializable {

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

    @Column(name = "portfolio_scope", length = 32)
    private String portfolioScope; // null or CONSOLIDATED, PORTFOLIO

    @Column(name = "event_classification", nullable = false, length = 64)
    private String eventClassification;
    // INVESTMENT_EVIDENCE_CHANGE, PERFORMANCE_CHANGE, PORTFOLIO_EXPOSURE_CHANGE,
    // HOLDINGS_DISCLOSURE_CHANGE, DATA_FRESHNESS_CHANGE, DATA_QUALITY_CHANGE,
    // SOURCE_REVISION, METHODOLOGY_CHANGE, COVERAGE_CHANGE, THESIS_CONDITION_TRIGGERED

    @Column(name = "severity", nullable = false, length = 16)
    private String severity; // INFO, LOW, MEDIUM, HIGH

    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private String lifecycleStatus = "NEW"; // NEW, ACKNOWLEDGED, INVESTIGATING, RESOLVED, DISMISSED

    @Column(name = "metric_or_evidence_id", nullable = false, length = 64)
    private String metricOrEvidenceId;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "current_value")
    private String currentValue;

    @Column(name = "change_magnitude", precision = 19, scale = 4)
    private BigDecimal changeMagnitude;

    @Column(name = "unit_or_format", length = 32)
    private String unitOrFormat;

    @Column(name = "threshold_classification", length = 32)
    private String thresholdClassification = "SYSTEM_CANDIDATE_THRESHOLD"; // SYSTEM_CANDIDATE_THRESHOLD, INVESTOR_DEFINED_THRESHOLD

    @Column(name = "evidence_state", length = 32)
    private String evidenceState = "AVAILABLE"; // AVAILABLE, STALE, MISSING, INSUFFICIENT_HISTORY, DATA_QUALITY_LIMITED, UNRESOLVED, METHODOLOGY_INCOMPATIBLE

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "availability_time")
    private ZonedDateTime availabilityTime;

    @Column(name = "ingestion_timestamp")
    private ZonedDateTime ingestionTimestamp;

    @Column(name = "source_revision", length = 64)
    private String sourceRevision;

    @Column(name = "previous_observation_date")
    private LocalDate previousObservationDate;

    @Column(name = "current_observation_date")
    private LocalDate currentObservationDate;

    @Column(name = "knowledge_cutoff")
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "source_reference")
    private String sourceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private InvestorMonitoringExecution execution;

    @Column(name = "metric_registry_version", length = 32)
    private String metricRegistryVersion = "V1";

    @Column(name = "methodology_version", length = 32)
    private String methodologyVersion = "PHASE_2H_FROZEN";

    @Column(name = "evidence_coverage_info", columnDefinition = "text")
    private String evidenceCoverageInfo;

    @Column(name = "explanation", nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "trigger_rationale", columnDefinition = "text")
    private String triggerRationale;

    @Column(name = "known_limitations", columnDefinition = "text")
    private String knownLimitations;

    @Column(name = "is_data_artifact", nullable = false)
    private Boolean isDataArtifact = false;

    @Column(name = "artifact_explanation", columnDefinition = "text")
    private String artifactExplanation;

    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    @Column(name = "associated_thesis_key", length = 128)
    private String associatedThesisKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "associated_condition_id")
    private InvestorMonitoringCondition associatedCondition;

    @Column(name = "associated_follow_up_id", length = 128)
    private String associatedFollowUpId;

    @Column(name = "investigation_notes", columnDefinition = "text")
    private String investigationNotes;

    @Column(name = "acknowledged_at")
    private ZonedDateTime acknowledgedAt;

    @Column(name = "investigating_at")
    private ZonedDateTime investigatingAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "dismissed_at")
    private ZonedDateTime dismissedAt;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<InvestorMonitoringEventNote> notes = new ArrayList<>();

    public InvestorMonitoringEvent() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (lifecycleStatus == null) lifecycleStatus = "NEW";
        if (isDataArtifact == null) isDataArtifact = false;
        if (metricRegistryVersion == null) metricRegistryVersion = "V1";
        if (methodologyVersion == null) methodologyVersion = "PHASE_2H_FROZEN";
        if (evidenceState == null) evidenceState = "AVAILABLE";
        if (thresholdClassification == null) thresholdClassification = "SYSTEM_CANDIDATE_THRESHOLD";
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

    public String getPortfolioScope() { return portfolioScope; }
    public void setPortfolioScope(String portfolioScope) { this.portfolioScope = portfolioScope; }

    public String getEventClassification() { return eventClassification; }
    public void setEventClassification(String eventClassification) { this.eventClassification = eventClassification; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public String getMetricOrEvidenceId() { return metricOrEvidenceId; }
    public void setMetricOrEvidenceId(String metricOrEvidenceId) { this.metricOrEvidenceId = metricOrEvidenceId; }

    public String getMetricName() { return metricName; }
    public String getTitle() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getCurrentValue() { return currentValue; }
    public void setCurrentValue(String currentValue) { this.currentValue = currentValue; }

    public BigDecimal getChangeMagnitude() { return changeMagnitude; }
    public void setChangeMagnitude(BigDecimal changeMagnitude) { this.changeMagnitude = changeMagnitude; }

    public String getUnitOrFormat() { return unitOrFormat; }
    public void setUnitOrFormat(String unitOrFormat) { this.unitOrFormat = unitOrFormat; }

    public String getThresholdClassification() { return thresholdClassification; }
    public void setThresholdClassification(String thresholdClassification) { this.thresholdClassification = thresholdClassification; }

    public String getEvidenceState() { return evidenceState; }
    public void setEvidenceState(String evidenceState) { this.evidenceState = evidenceState; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public ZonedDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(ZonedDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public ZonedDateTime getIngestionTimestamp() { return ingestionTimestamp; }
    public void setIngestionTimestamp(ZonedDateTime ingestionTimestamp) { this.ingestionTimestamp = ingestionTimestamp; }

    public String getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(String sourceRevision) { this.sourceRevision = sourceRevision; }

    public LocalDate getPreviousObservationDate() { return previousObservationDate; }
    public void setPreviousObservationDate(LocalDate previousObservationDate) { this.previousObservationDate = previousObservationDate; }

    public LocalDate getCurrentObservationDate() { return currentObservationDate; }
    public void setCurrentObservationDate(LocalDate currentObservationDate) { this.currentObservationDate = currentObservationDate; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public InvestorMonitoringExecution getExecution() { return execution; }
    public void setExecution(InvestorMonitoringExecution execution) { this.execution = execution; }

    public String getMetricRegistryVersion() { return metricRegistryVersion; }
    public void setMetricRegistryVersion(String metricRegistryVersion) { this.metricRegistryVersion = metricRegistryVersion; }

    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }

    public String getEvidenceCoverageInfo() { return evidenceCoverageInfo; }
    public void setEvidenceCoverageInfo(String evidenceCoverageInfo) { this.evidenceCoverageInfo = evidenceCoverageInfo; }

    public String getExplanation() { return explanation; }
    public String getSummaryText() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getTriggerRationale() { return triggerRationale; }
    public void setTriggerRationale(String triggerRationale) { this.triggerRationale = triggerRationale; }

    public String getKnownLimitations() { return knownLimitations; }
    public void setKnownLimitations(String knownLimitations) { this.knownLimitations = knownLimitations; }

    public Boolean getIsDataArtifact() { return isDataArtifact; }
    public void setIsDataArtifact(Boolean isDataArtifact) { this.isDataArtifact = isDataArtifact; }

    public String getArtifactExplanation() { return artifactExplanation; }
    public void setArtifactExplanation(String artifactExplanation) { this.artifactExplanation = artifactExplanation; }

    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }

    public String getAssociatedThesisKey() { return associatedThesisKey; }
    public void setAssociatedThesisKey(String associatedThesisKey) { this.associatedThesisKey = associatedThesisKey; }

    public InvestorMonitoringCondition getAssociatedCondition() { return associatedCondition; }
    public void setAssociatedCondition(InvestorMonitoringCondition associatedCondition) { this.associatedCondition = associatedCondition; }

    public String getAssociatedFollowUpId() { return associatedFollowUpId; }
    public void setAssociatedFollowUpId(String associatedFollowUpId) { this.associatedFollowUpId = associatedFollowUpId; }

    public String getInvestigationNotes() { return investigationNotes; }
    public void setInvestigationNotes(String investigationNotes) { this.investigationNotes = investigationNotes; }

    public ZonedDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(ZonedDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public ZonedDateTime getInvestigatingAt() { return investigatingAt; }
    public void setInvestigatingAt(ZonedDateTime investigatingAt) { this.investigatingAt = investigatingAt; }

    public ZonedDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(ZonedDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public ZonedDateTime getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(ZonedDateTime dismissedAt) { this.dismissedAt = dismissedAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getTriggeredAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<InvestorMonitoringEventNote> getNotes() { return notes; }
    public void setNotes(List<InvestorMonitoringEventNote> notes) { this.notes = notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorMonitoringEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
