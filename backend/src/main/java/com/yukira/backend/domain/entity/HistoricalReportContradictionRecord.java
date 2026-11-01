package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity tracking structured contradictions between previous reports and actual repository state (Task 415).
 */
@Entity
@Table(name = "historical_report_contradiction_record", indexes = {
    @Index(name = "idx_hist_contradiction_key", columnList = "contradiction_key")
})
public class HistoricalReportContradictionRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contradiction_key", nullable = false, unique = true, length = 100)
    private String contradictionKey;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @Column(name = "previous_claim", nullable = false, columnDefinition = "TEXT")
    private String previousClaim;

    @Column(name = "current_repository_reality", nullable = false, columnDefinition = "TEXT")
    private String currentRepositoryReality;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity; // HIGH, MEDIUM, LOW

    @Column(name = "reconciliation_resolution", nullable = false, columnDefinition = "TEXT")
    private String reconciliationResolution;

    @Column(name = "reconciled_at", nullable = false)
    private OffsetDateTime reconciledAt = OffsetDateTime.now();

    public HistoricalReportContradictionRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContradictionKey() { return contradictionKey; }
    public void setContradictionKey(String contradictionKey) { this.contradictionKey = contradictionKey; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getPreviousClaim() { return previousClaim; }
    public void setPreviousClaim(String previousClaim) { this.previousClaim = previousClaim; }

    public String getCurrentRepositoryReality() { return currentRepositoryReality; }
    public void setCurrentRepositoryReality(String currentRepositoryReality) { this.currentRepositoryReality = currentRepositoryReality; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getReconciliationResolution() { return reconciliationResolution; }
    public void setReconciliationResolution(String reconciliationResolution) { this.reconciliationResolution = reconciliationResolution; }

    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}
