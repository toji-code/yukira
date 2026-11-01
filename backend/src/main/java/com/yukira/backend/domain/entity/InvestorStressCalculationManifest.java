package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Durable calculation manifest entity (Task 85).
 * Stores the complete calculation manifest linking calculation run, investor, portfolio snapshot,
 * input hash, methodology version, governance status, and serialized manifest payload.
 */
@Entity
@Table(name = "investor_stress_calculation_manifest")
public class InvestorStressCalculationManifest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @Column(name = "calculation_run_code", nullable = false, length = 100)
    private String calculationRunCode;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "snapshot_id", length = 100)
    private String snapshotId;

    @Column(name = "snapshot_fingerprint", length = 64)
    private String snapshotFingerprint;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "methodology_version", nullable = false, length = 50)
    private String methodologyVersion = "PORTFOLIO_STRESS_METHODOLOGY_V2";

    @Column(name = "governance_status", nullable = false, length = 50)
    private String governanceStatus = "CANDIDATE";

    @Column(name = "knowledge_cutoff")
    private OffsetDateTime knowledgeCutoff;

    @Column(name = "analysis_cutoff_date")
    private LocalDate analysisCutoffDate;

    @Column(name = "manifest_payload_json", nullable = false, columnDefinition = "text")
    private String manifestPayloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public InvestorStressCalculationManifest() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public String getCalculationRunCode() { return calculationRunCode; }
    public void setCalculationRunCode(String calculationRunCode) { this.calculationRunCode = calculationRunCode; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }

    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }

    public String getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(String methodologyVersion) { this.methodologyVersion = methodologyVersion; }

    public String getGovernanceStatus() { return governanceStatus; }
    public void setGovernanceStatus(String governanceStatus) { this.governanceStatus = governanceStatus; }

    public OffsetDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(OffsetDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public LocalDate getAnalysisCutoffDate() { return analysisCutoffDate; }
    public void setAnalysisCutoffDate(LocalDate analysisCutoffDate) { this.analysisCutoffDate = analysisCutoffDate; }

    public String getManifestPayloadJson() { return manifestPayloadJson; }
    public void setManifestPayloadJson(String manifestPayloadJson) { this.manifestPayloadJson = manifestPayloadJson; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorStressCalculationManifest that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
