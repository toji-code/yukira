package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "nav_observation", uniqueConstraints = {
    @UniqueConstraint(name = "uq_nav_observation", columnNames = {"scheme_option_id", "effective_date", "revision_seq"})
})
public class NavObservation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "nav_value", nullable = false, precision = 20, scale = 8)
    private BigDecimal navValue;

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

    public NavObservation() {}

    public NavObservation(SchemeOption schemeOption, LocalDate effectiveDate, BigDecimal navValue,
                          Integer revisionSeq, OffsetDateTime availabilityTime) {
        this.schemeOption = schemeOption;
        this.effectiveDate = effectiveDate;
        this.navValue = navValue;
        this.revisionSeq = revisionSeq;
        this.availabilityTime = availabilityTime;
        this.ingestionTime = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public BigDecimal getNavValue() { return navValue; }
    public void setNavValue(BigDecimal navValue) { this.navValue = navValue; }

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
}
