package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "scheme_investment_terms", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scheme_investment_terms", columnNames = {"scheme_id", "as_of_date"})
})
public class SchemeInvestmentTerms implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "min_sip_amount", precision = 15, scale = 2)
    private BigDecimal minSipAmount;

    @Column(name = "sip_frequencies", length = 255)
    private String sipFrequencies;

    @Column(name = "min_lumpsum_amount", precision = 15, scale = 2)
    private BigDecimal minLumpsumAmount;

    @Column(name = "min_additional_amount", precision = 15, scale = 2)
    private BigDecimal minAdditionalAmount;

    @Column(name = "lock_in_period_days")
    private Integer lockInPeriodDays;

    @Column(name = "exit_load_description", columnDefinition = "TEXT")
    private String exitLoadDescription;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "source_document_title", length = 255)
    private String sourceDocumentTitle;

    @Column(name = "quality_assessment", nullable = false, length = 30)
    private String qualityAssessment = "VALID";

    public SchemeInvestmentTerms() {}

    public SchemeInvestmentTerms(Scheme scheme, LocalDate asOfDate) {
        this.scheme = scheme;
        this.asOfDate = asOfDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public BigDecimal getMinSipAmount() { return minSipAmount; }
    public void setMinSipAmount(BigDecimal minSipAmount) { this.minSipAmount = minSipAmount; }

    public String getSipFrequencies() { return sipFrequencies; }
    public void setSipFrequencies(String sipFrequencies) { this.sipFrequencies = sipFrequencies; }

    public BigDecimal getMinLumpsumAmount() { return minLumpsumAmount; }
    public void setMinLumpsumAmount(BigDecimal minLumpsumAmount) { this.minLumpsumAmount = minLumpsumAmount; }

    public BigDecimal getMinAdditionalAmount() { return minAdditionalAmount; }
    public void setMinAdditionalAmount(BigDecimal minAdditionalAmount) { this.minAdditionalAmount = minAdditionalAmount; }

    public Integer getLockInPeriodDays() { return lockInPeriodDays; }
    public void setLockInPeriodDays(Integer lockInPeriodDays) { this.lockInPeriodDays = lockInPeriodDays; }

    public String getExitLoadDescription() { return exitLoadDescription; }
    public void setExitLoadDescription(String exitLoadDescription) { this.exitLoadDescription = exitLoadDescription; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public String getSourceDocumentTitle() { return sourceDocumentTitle; }
    public void setSourceDocumentTitle(String sourceDocumentTitle) { this.sourceDocumentTitle = sourceDocumentTitle; }

    public String getQualityAssessment() { return qualityAssessment; }
    public void setQualityAssessment(String qualityAssessment) { this.qualityAssessment = qualityAssessment; }
}
