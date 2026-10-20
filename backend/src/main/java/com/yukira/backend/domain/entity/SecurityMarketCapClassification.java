package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Historical Point-in-Time Market-Cap Classification record published by AMFI.
 *
 * Epistemic & Bitemporal Principles:
 * 1. Effective Time: [effectiveFrom, effectiveTo] when the categorization applies to mutual fund portfolios.
 * 2. Availability Time: when AMFI published the ranking list to the public/investors.
 * 3. Ingestion Time: when YUKIRA captured and recorded the observation.
 * 4. Revision Tracking: revisionSeq increments monotonically; earlier observations are never overwritten.
 * 5. Cryptographic Lineage: linked directly to source_artifact (URL, SHA-256 hash, raw bytes).
 */
@Entity
@Table(name = "security_market_cap_classification", uniqueConstraints = {
    @UniqueConstraint(name = "uq_sec_mcap_pit", columnNames = {"isin", "ranking_period_end_date", "revision_seq"})
})
public class SecurityMarketCapClassification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id")
    private Security security;

    @Column(name = "isin", nullable = false, length = 20)
    private String isin;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "bse_symbol", length = 50)
    private String bseSymbol;

    @Column(name = "nse_symbol", length = 50)
    private String nseSymbol;

    @Column(name = "market_cap_rank", nullable = false)
    private Integer marketCapRank;

    @Column(name = "market_cap_category", nullable = false, length = 30)
    private String marketCapCategory;

    @Column(name = "avg_market_cap_crores", precision = 24, scale = 4)
    private BigDecimal avgMarketCapCrores;

    @Column(name = "ranking_period", nullable = false, length = 50)
    private String rankingPeriod;

    @Column(name = "ranking_period_end_date", nullable = false)
    private LocalDate rankingPeriodEndDate;

    @Column(name = "source_publication_date", nullable = false)
    private LocalDate sourcePublicationDate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "availability_time", nullable = false)
    private OffsetDateTime availabilityTime;

    @Column(name = "ingestion_time", nullable = false)
    private OffsetDateTime ingestionTime = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq = 1;

    @Column(name = "is_latest_revision", nullable = false)
    private Boolean isLatestRevision = true;

    @Column(name = "validation_status", nullable = false, length = 30)
    private String validationStatus = "VALIDATED";

    @Column(name = "mapping_status", nullable = false, length = 30)
    private String mappingStatus = "MAPPED";

    public SecurityMarketCapClassification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getBseSymbol() { return bseSymbol; }
    public void setBseSymbol(String bseSymbol) { this.bseSymbol = bseSymbol; }

    public String getNseSymbol() { return nseSymbol; }
    public void setNseSymbol(String nseSymbol) { this.nseSymbol = nseSymbol; }

    public Integer getMarketCapRank() { return marketCapRank; }
    public void setMarketCapRank(Integer marketCapRank) { this.marketCapRank = marketCapRank; }

    public String getMarketCapCategory() { return marketCapCategory; }
    public void setMarketCapCategory(String marketCapCategory) { this.marketCapCategory = marketCapCategory; }

    public BigDecimal getAvgMarketCapCrores() { return avgMarketCapCrores; }
    public void setAvgMarketCapCrores(BigDecimal avgMarketCapCrores) { this.avgMarketCapCrores = avgMarketCapCrores; }

    public String getRankingPeriod() { return rankingPeriod; }
    public void setRankingPeriod(String rankingPeriod) { this.rankingPeriod = rankingPeriod; }

    public LocalDate getRankingPeriodEndDate() { return rankingPeriodEndDate; }
    public void setRankingPeriodEndDate(LocalDate rankingPeriodEndDate) { this.rankingPeriodEndDate = rankingPeriodEndDate; }

    public LocalDate getSourcePublicationDate() { return sourcePublicationDate; }
    public void setSourcePublicationDate(LocalDate sourcePublicationDate) { this.sourcePublicationDate = sourcePublicationDate; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public OffsetDateTime getIngestionTime() { return ingestionTime; }
    public void setIngestionTime(OffsetDateTime ingestionTime) { this.ingestionTime = ingestionTime; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }

    public Boolean getLatestRevision() { return isLatestRevision; }
    public void setLatestRevision(Boolean latestRevision) { isLatestRevision = latestRevision; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }
}
