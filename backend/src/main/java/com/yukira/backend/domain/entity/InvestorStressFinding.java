package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Durable research evidence repository entity for historical stress findings (Tasks 80, 81).
 * Supports explicit versioning, lifecycle transitions (ACTIVE, SUPERSEDED, RESOLVED, DISMISSED),
 * and point-in-time calculation linkage.
 */
@Entity
@Table(name = "investor_stress_finding")
public class InvestorStressFinding implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "finding_id", nullable = false, length = 100)
    private String findingId;

    @Column(name = "finding_version", nullable = false)
    private Integer findingVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @Column(name = "calculation_run_code", length = 100)
    private String calculationRunCode;

    @Column(name = "snapshot_id", length = 100)
    private String snapshotId;

    @Column(name = "snapshot_fingerprint", length = 64)
    private String snapshotFingerprint;

    @Column(name = "episode_code", nullable = false, length = 50)
    private String episodeCode;

    @Column(name = "episode_name", length = 200)
    private String episodeName;

    @Column(name = "finding_type", nullable = false, length = 100)
    private String findingType;

    @Column(name = "finding_severity", nullable = false, length = 50)
    private String findingSeverity;

    @Column(name = "finding_headline", nullable = false, columnDefinition = "text")
    private String findingHeadline;

    @Column(name = "finding_detail", columnDefinition = "text")
    private String findingDetail;

    @Column(name = "methodology_version", nullable = false, length = 50)
    private String methodologyVersion = "PORTFOLIO_STRESS_METHODOLOGY_V2";

    @Column(name = "governance_status", nullable = false, length = 50)
    private String governanceStatus = "CANDIDATE";

    @Column(name = "knowledge_cutoff_date")
    private OffsetDateTime knowledgeCutoffDate;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "lifecycle_status", nullable = false, length = 50)
    private String lifecycleStatus = "ACTIVE"; // ACTIVE, SUPERSEDED, RESOLVED, DISMISSED

    @Column(name = "superseded_by_finding_id")
    private Long supersededByFindingId;

    @Column(name = "transition_reason", columnDefinition = "text")
    private String transitionReason;

    @Column(name = "transition_by", length = 50)
    private String transitionBy = "SYSTEM"; // SYSTEM, INVESTOR

    @Column(name = "investor_notes", columnDefinition = "text")
    private String investorNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public InvestorStressFinding() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }

    public Integer getFindingVersion() { return findingVersion; }
    public void setFindingVersion(Integer findingVersion) { this.findingVersion = findingVersion; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public String getCalculationRunCode() { return calculationRunCode; }
    public void setCalculationRunCode(String calculationRunCode) { this.calculationRunCode = calculationRunCode; }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }

    public String getEpisodeCode() { return episodeCode; }
    public void setEpisodeCode(String episodeCode) { this.episodeCode = episodeCode; }

    public String getEpisodeName() { return episodeName; }
    public void setEpisodeName(String episodeName) { this.episodeName = episodeName; }

    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }

    public String getFindingSeverity() { return findingSeverity; }
    public void setFindingSeverity(String findingSeverity) { this.findingSeverity = findingSeverity; }

    public String getFindingHeadline() { return findingHeadline; }
    public void setFindingHeadline(String findingHeadline) { this.findingHeadline = findingHeadline; }

    public String getFindingDetail() { return findingDetail; }
    public void setFindingDetail(String findingDetail) { this.findingDetail = findingDetail; }

    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }

    public String getGovernanceStatus() { return governanceStatus; }
    public void setGovernanceStatus(String governanceStatus) { this.governanceStatus = governanceStatus; }

    public OffsetDateTime getKnowledgeCutoffDate() { return knowledgeCutoffDate; }
    public void setKnowledgeCutoffDate(OffsetDateTime knowledgeCutoffDate) { this.knowledgeCutoffDate = knowledgeCutoffDate; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public Long getSupersededByFindingId() { return supersededByFindingId; }
    public void setSupersededByFindingId(Long supersededByFindingId) { this.supersededByFindingId = supersededByFindingId; }

    public String getTransitionReason() { return transitionReason; }
    public void setTransitionReason(String transitionReason) { this.transitionReason = transitionReason; }

    public String getTransitionBy() { return transitionBy; }
    public void setTransitionBy(String transitionBy) { this.transitionBy = transitionBy; }

    public String getInvestorNotes() { return investorNotes; }
    public void setInvestorNotes(String investorNotes) { this.investorNotes = investorNotes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorStressFinding that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
