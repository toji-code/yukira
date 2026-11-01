package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_comparison_research_version")
public class InvestorComparisonResearchVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comparison_research_id", nullable = false)
    private InvestorComparisonResearch comparisonResearch;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "scheme_option_ids_csv", nullable = false)
    private String schemeOptionIdsCsv;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

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

    @Column(name = "version_reason")
    private String versionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorComparisonResearchVersion() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorComparisonResearch getComparisonResearch() { return comparisonResearch; }
    public void setComparisonResearch(InvestorComparisonResearch comparisonResearch) { this.comparisonResearch = comparisonResearch; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getSchemeOptionIdsCsv() { return schemeOptionIdsCsv; }
    public void setSchemeOptionIdsCsv(String schemeOptionIdsCsv) { this.schemeOptionIdsCsv = schemeOptionIdsCsv; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }

    public String getCompatibilityStatus() { return compatibilityStatus; }
    public void setCompatibilityStatus(String compatibilityStatus) { this.compatibilityStatus = compatibilityStatus; }

    public String getMetricsSummaryJson() { return metricsSummaryJson; }
    public void setMetricsSummaryJson(String metricsSummaryJson) { this.metricsSummaryJson = metricsSummaryJson; }

    public String getInvestorThesis() { return investorThesis; }
    public void setInvestorThesis(String investorThesis) { this.investorThesis = investorThesis; }

    public String getInvestorNotes() { return investorNotes; }
    public void setInvestorNotes(String investorNotes) { this.investorNotes = investorNotes; }

    public String getVersionReason() { return versionReason; }
    public void setVersionReason(String versionReason) { this.versionReason = versionReason; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorComparisonResearchVersion that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

