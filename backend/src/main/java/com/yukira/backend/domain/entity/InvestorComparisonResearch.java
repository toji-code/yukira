package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entity representing a preserved multi-fund comparative research investigation (Task 155).
 * Enables investors to save structured comparison snapshots, compatibility evidence,
 * and working hypotheses to their personal research workspace.
 */
@Entity
@Table(name = "investor_comparison_research")
public class InvestorComparisonResearch implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "comparison_title", nullable = false)
    private String comparisonTitle;

    @Column(name = "scheme_option_ids_csv", nullable = false)
    private String schemeOptionIdsCsv;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "knowledge_cutoff", nullable = false)
    private OffsetDateTime knowledgeCutoff;

    @Column(name = "benchmark_code", length = 64)
    private String benchmarkCode;

    @Column(name = "compatibility_status", nullable = false, length = 64)
    private String compatibilityStatus;

    @Column(name = "metrics_summary_json", columnDefinition = "text")
    private String metricsSummaryJson;

    @Column(name = "investor_thesis", columnDefinition = "text")
    private String investorThesis;

    @Column(name = "investor_notes", columnDefinition = "text")
    private String investorNotes;

    @Column(name = "associated_finding_id", length = 64)
    private String associatedFindingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public InvestorComparisonResearch() {}

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Investor getInvestor() {
        return investor;
    }

    public void setInvestor(Investor investor) {
        this.investor = investor;
    }

    public String getComparisonTitle() {
        return comparisonTitle;
    }

    public String getTitle() {
        return comparisonTitle;
    }

    public void setComparisonTitle(String comparisonTitle) {
        this.comparisonTitle = comparisonTitle;
    }

    public String getSchemeOptionIdsCsv() {
        return schemeOptionIdsCsv;
    }

    public void setSchemeOptionIdsCsv(String schemeOptionIdsCsv) {
        this.schemeOptionIdsCsv = schemeOptionIdsCsv;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public OffsetDateTime getKnowledgeCutoff() {
        return knowledgeCutoff;
    }

    public void setKnowledgeCutoff(OffsetDateTime knowledgeCutoff) {
        this.knowledgeCutoff = knowledgeCutoff;
    }

    public String getBenchmarkCode() {
        return benchmarkCode;
    }

    public void setBenchmarkCode(String benchmarkCode) {
        this.benchmarkCode = benchmarkCode;
    }

    public String getCompatibilityStatus() {
        return compatibilityStatus;
    }

    public void setCompatibilityStatus(String compatibilityStatus) {
        this.compatibilityStatus = compatibilityStatus;
    }

    public String getMetricsSummaryJson() {
        return metricsSummaryJson;
    }

    public void setMetricsSummaryJson(String metricsSummaryJson) {
        this.metricsSummaryJson = metricsSummaryJson;
    }

    public String getInvestorThesis() {
        return investorThesis;
    }

    public void setInvestorThesis(String investorThesis) {
        this.investorThesis = investorThesis;
    }

    public String getInvestorNotes() {
        return investorNotes;
    }

    public void setInvestorNotes(String investorNotes) {
        this.investorNotes = investorNotes;
    }

    public String getAssociatedFindingId() {
        return associatedFindingId;
    }

    public void setAssociatedFindingId(String associatedFindingId) {
        this.associatedFindingId = associatedFindingId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorComparisonResearch that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
