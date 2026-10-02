package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "portfolio_snapshot", uniqueConstraints = {
    @UniqueConstraint(name = "uq_portfolio_snapshot", columnNames = {"scheme_option_id", "portfolio_date", "revision_seq"})
})
public class PortfolioSnapshot implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @Column(name = "portfolio_date", nullable = false)
    private LocalDate portfolioDate;

    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq = 1;

    @Column(name = "is_latest_revision", nullable = false)
    private Boolean isLatestRevision = true;

    @Column(name = "availability_time", nullable = false)
    private OffsetDateTime availabilityTime;
    
    @Column(name = "reported_total_net_assets", precision = 24, scale = 4)
    private BigDecimal reportedTotalNetAssets;
    
    @Column(name = "reported_holdings_count")
    private Integer reportedHoldingsCount;
    
    @Column(name = "sum_reported_weights", precision = 10, scale = 6)
    private BigDecimal sumReportedWeights;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    public PortfolioSnapshot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }
    public LocalDate getPortfolioDate() { return portfolioDate; }
    public void setPortfolioDate(LocalDate portfolioDate) { this.portfolioDate = portfolioDate; }
    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }
    public Boolean getLatestRevision() { return isLatestRevision; }
    public void setLatestRevision(Boolean latestRevision) { isLatestRevision = latestRevision; }
    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }
    public BigDecimal getReportedTotalNetAssets() { return reportedTotalNetAssets; }
    public void setReportedTotalNetAssets(BigDecimal reportedTotalNetAssets) { this.reportedTotalNetAssets = reportedTotalNetAssets; }
    public Integer getReportedHoldingsCount() { return reportedHoldingsCount; }
    public void setReportedHoldingsCount(Integer reportedHoldingsCount) { this.reportedHoldingsCount = reportedHoldingsCount; }
    public BigDecimal getSumReportedWeights() { return sumReportedWeights; }
    public void setSumReportedWeights(BigDecimal sumReportedWeights) { this.sumReportedWeights = sumReportedWeights; }
    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }
}
