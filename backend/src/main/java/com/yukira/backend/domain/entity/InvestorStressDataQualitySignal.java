package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Durable data quality signal tracking entity for portfolio stress analysis (Task 82).
 * Preserves source artifact and revision lineage with explicit lifecycle states.
 */
@Entity
@Table(name = "investor_stress_data_quality_signal")
public class InvestorStressDataQualitySignal implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @Column(name = "calculation_run_code", length = 100)
    private String calculationRunCode;

    @Column(name = "signal_code", nullable = false, length = 100)
    private String signalCode;

    @Column(name = "signal_dimension", nullable = false, length = 50)
    private String signalDimension; // QUALITY, VERIFICATION, REVISION, FRESHNESS, PRESENCE, INTEGRITY

    @Column(name = "signal_state", nullable = false, length = 50)
    private String signalState = "ACTIVE_WARNING"; // ACTIVE_WARNING, ACKNOWLEDGED, RESOLVED, SUPERSEDED_BY_SOURCE_REVISION, INFORMATIONAL

    @Column(name = "constituent_code", length = 50)
    private String constituentCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "source_revision_seq")
    private Integer sourceRevisionSeq;

    @Column(name = "first_detected_at")
    private OffsetDateTime firstDetectedAt = OffsetDateTime.now();

    @Column(name = "latest_detected_at")
    private OffsetDateTime latestDetectedAt = OffsetDateTime.now();

    @Column(name = "observation_interval_start")
    private java.time.LocalDate observationIntervalStart;

    @Column(name = "observation_interval_end")
    private java.time.LocalDate observationIntervalEnd;

    @Column(name = "benchmark_code", length = 50)
    private String benchmarkCode;

    @Column(name = "superseded_by_revision_seq")
    private Integer supersededByRevisionSeq;

    @Column(name = "investor_action_notes", columnDefinition = "text")
    private String investorActionNotes;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "evidence_context", columnDefinition = "text")
    private String evidenceContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public InvestorStressDataQualitySignal() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public String getCalculationRunCode() { return calculationRunCode; }
    public void setCalculationRunCode(String calculationRunCode) { this.calculationRunCode = calculationRunCode; }

    public String getSignalCode() { return signalCode; }
    public void setSignalCode(String signalCode) { this.signalCode = signalCode; }

    public String getSignalDimension() { return signalDimension; }
    public void setSignalDimension(String signalDimension) { this.signalDimension = signalDimension; }

    public String getSignalState() { return signalState; }
    public void setSignalState(String signalState) { this.signalState = signalState; }

    public String getConstituentCode() { return constituentCode; }
    public void setConstituentCode(String constituentCode) { this.constituentCode = constituentCode; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public Integer getSourceRevisionSeq() { return sourceRevisionSeq; }
    public void setSourceRevisionSeq(Integer sourceRevisionSeq) { this.sourceRevisionSeq = sourceRevisionSeq; }

    public OffsetDateTime getFirstDetectedAt() { return firstDetectedAt; }
    public void setFirstDetectedAt(OffsetDateTime firstDetectedAt) { this.firstDetectedAt = firstDetectedAt; }

    public OffsetDateTime getLatestDetectedAt() { return latestDetectedAt; }
    public void setLatestDetectedAt(OffsetDateTime latestDetectedAt) { this.latestDetectedAt = latestDetectedAt; }

    public java.time.LocalDate getObservationIntervalStart() { return observationIntervalStart; }
    public void setObservationIntervalStart(java.time.LocalDate observationIntervalStart) { this.observationIntervalStart = observationIntervalStart; }

    public java.time.LocalDate getObservationIntervalEnd() { return observationIntervalEnd; }
    public void setObservationIntervalEnd(java.time.LocalDate observationIntervalEnd) { this.observationIntervalEnd = observationIntervalEnd; }

    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }

    public Integer getSupersededByRevisionSeq() { return supersededByRevisionSeq; }
    public void setSupersededByRevisionSeq(Integer supersededByRevisionSeq) { this.supersededByRevisionSeq = supersededByRevisionSeq; }

    public String getInvestorActionNotes() { return investorActionNotes; }
    public void setInvestorActionNotes(String investorActionNotes) { this.investorActionNotes = investorActionNotes; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getEvidenceContext() { return evidenceContext; }
    public void setEvidenceContext(String evidenceContext) { this.evidenceContext = evidenceContext; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorStressDataQualitySignal that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
