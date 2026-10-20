package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Historical Point-in-Time Benchmark Constituent observation record.
 *
 * Epistemic & Bitemporal Principles (AGENTS.md):
 * 1. Effective Time: effectiveDate when the constituent composition applies to benchmark performance.
 * 2. Availability Time: when NSE Indices published the constituent list/factsheet.
 * 3. Ingestion Time: when YUKIRA captured and validated the artifact.
 * 4. Revision Tracking: revisionSeq increments monotonically; earlier observations are preserved.
 * 5. Cryptographic Lineage: linked to source_artifact with SHA-256 digest.
 * 6. Zero Fake Data: If weights are proprietary/withheld, weight is stored as null and flagged PARTIAL_DISCLOSURE.
 */
@Entity
@Table(name = "benchmark_constituent", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bm_constituent_pit", columnNames = {"benchmark_id", "isin", "effective_date", "revision_seq"})
})
public class BenchmarkConstituent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id")
    private Security security;

    @Column(name = "isin", nullable = false, length = 20)
    private String isin;

    @Column(name = "symbol", length = 50)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "weight", precision = 10, scale = 6)
    private BigDecimal weight;

    @Column(name = "weight_unit", nullable = false, length = 20)
    private String weightUnit = "PERCENTAGE";

    @Column(name = "is_member", nullable = false)
    private Boolean isMember = true;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

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

    @Column(name = "mapping_status", nullable = false, length = 30)
    private String mappingStatus = "MAPPED";

    @Column(name = "validation_status", nullable = false, length = 30)
    private String validationStatus = "VALIDATED";

    public BenchmarkConstituent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }

    public Boolean getIsMember() { return isMember; }
    public void setIsMember(Boolean isMember) { this.isMember = isMember; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public OffsetDateTime getIngestionTime() { return ingestionTime; }
    public void setIngestionTime(OffsetDateTime ingestionTime) { this.ingestionTime = ingestionTime; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }

    public Boolean getIsLatestRevision() { return isLatestRevision; }
    public void setIsLatestRevision(Boolean isLatestRevision) { this.isLatestRevision = isLatestRevision; }

    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
}
