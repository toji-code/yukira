package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "risk_free_observation", uniqueConstraints = {
    @UniqueConstraint(name = "uq_risk_free_observation", columnNames = {"benchmark_code", "effective_date", "revision_seq"})
})
public class RiskFreeObservation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benchmark_code", nullable = false, length = 50)
    private String benchmarkCode = "FBIL_91D_TBILL";

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "quoted_yield", nullable = false, precision = 12, scale = 8)
    private BigDecimal quotedYield;

    @Column(name = "daycount_convention", nullable = false, length = 30)
    private String daycountConvention = "ACT_365";

    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq = 1;

    @Column(name = "is_latest_revision", nullable = false)
    private Boolean isLatestRevision = true;

    @Column(name = "availability_time", nullable = false)
    private OffsetDateTime availabilityTime;

    @Column(name = "ingestion_time", nullable = false)
    private OffsetDateTime ingestionTime = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "quality_assessment", nullable = false, length = 30)
    private String qualityAssessment = "VALID";

    @Column(name = "verification_status", nullable = false, length = 30)
    private String verificationStatus = "VERIFIED";

    @Column(name = "revision_status", nullable = false, length = 30)
    private String revisionStatus = "ORIGINAL";

    @Column(name = "temporal_status", nullable = false, length = 30)
    private String temporalStatus = "CURRENT";

    @Column(name = "presence_status", nullable = false, length = 30)
    private String presenceStatus = "AVAILABLE";

    @Column(name = "integrity_condition", nullable = false, length = 30)
    private String integrityCondition = "NONE";

    public RiskFreeObservation() {}

    public RiskFreeObservation(String benchmarkCode, LocalDate effectiveDate, BigDecimal quotedYield,
                               String daycountConvention, Integer revisionSeq, OffsetDateTime availabilityTime) {
        this.benchmarkCode = benchmarkCode != null ? benchmarkCode : "FBIL_91D_TBILL";
        this.effectiveDate = effectiveDate;
        this.quotedYield = quotedYield;
        this.daycountConvention = daycountConvention != null ? daycountConvention : "ACT_365";
        this.revisionSeq = revisionSeq != null ? revisionSeq : 1;
        this.availabilityTime = availabilityTime;
        this.ingestionTime = OffsetDateTime.now();
    }

    public RiskFreeObservation(String benchmarkCode, LocalDate effectiveDate, BigDecimal quotedYield,
                               Integer revisionSeq, OffsetDateTime availabilityTime) {
        this(benchmarkCode, effectiveDate, quotedYield, "ACT_365", revisionSeq, availabilityTime);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public BigDecimal getQuotedYield() { return quotedYield; }
    public void setQuotedYield(BigDecimal quotedYield) { this.quotedYield = quotedYield; }

    public String getDaycountConvention() { return daycountConvention; }
    public void setDaycountConvention(String daycountConvention) { this.daycountConvention = daycountConvention; }

    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }

    public Boolean getLatestRevision() { return isLatestRevision; }
    public void setLatestRevision(Boolean latestRevision) { isLatestRevision = latestRevision; }

    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public OffsetDateTime getIngestionTime() { return ingestionTime; }
    public void setIngestionTime(OffsetDateTime ingestionTime) { this.ingestionTime = ingestionTime; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public String getQualityAssessment() { return qualityAssessment; }
    public void setQualityAssessment(String qualityAssessment) { this.qualityAssessment = qualityAssessment; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getRevisionStatus() { return revisionStatus; }
    public void setRevisionStatus(String revisionStatus) { this.revisionStatus = revisionStatus; }

    public String getTemporalStatus() { return temporalStatus; }
    public void setTemporalStatus(String temporalStatus) { this.temporalStatus = temporalStatus; }

    public String getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(String presenceStatus) { this.presenceStatus = presenceStatus; }

    public String getIntegrityCondition() { return integrityCondition; }
    public void setIntegrityCondition(String integrityCondition) { this.integrityCondition = integrityCondition; }
}
