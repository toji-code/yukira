package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_tax_classification_rule")
public class IndianMutualFundTaxRule implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 64)
    private String ruleCode;

    @Column(name = "category_pattern", nullable = false, length = 128)
    private String categoryPattern;

    @Column(name = "statutory_section", nullable = false, length = 64)
    private String statutorySection;

    @Column(name = "acquisition_start_date", nullable = false)
    private LocalDate acquisitionStartDate;

    @Column(name = "acquisition_end_date")
    private LocalDate acquisitionEndDate;

    @Column(name = "stcg_threshold_days", nullable = false)
    private Integer stcgThresholdDays = 365;

    @Column(name = "equity_exposure_min_percent", precision = 5, scale = 2)
    private BigDecimal equityExposureMinPercent;

    @Column(name = "stcg_tax_rate_percent", precision = 5, scale = 2)
    private BigDecimal stcgTaxRatePercent;

    @Column(name = "ltcg_tax_rate_percent", precision = 5, scale = 2)
    private BigDecimal ltcgTaxRatePercent;

    @Column(name = "indexation_allowed", nullable = false)
    private Boolean indexationAllowed = false;

    @Column(name = "provisional_notice", nullable = false, columnDefinition = "TEXT")
    private String provisionalNotice;

    @Column(name = "legal_instrument_ref", length = 255)
    private String legalInstrumentRef;

    @Column(name = "finance_act_ref", length = 100)
    private String financeActRef;

    @Column(name = "transfer_start_date")
    private LocalDate transferStartDate;

    @Column(name = "transfer_end_date")
    private LocalDate transferEndDate;

    @Column(name = "rule_version", length = 50)
    private String ruleVersion = "1.0";

    @Column(name = "review_status", length = 50)
    private String reviewStatus = "STATUTORY_BENCHMARKED";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public IndianMutualFundTaxRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getCategoryPattern() { return categoryPattern; }
    public void setCategoryPattern(String categoryPattern) { this.categoryPattern = categoryPattern; }

    public String getStatutorySection() { return statutorySection; }
    public void setStatutorySection(String statutorySection) { this.statutorySection = statutorySection; }

    public LocalDate getAcquisitionStartDate() { return acquisitionStartDate; }
    public void setAcquisitionStartDate(LocalDate acquisitionStartDate) { this.acquisitionStartDate = acquisitionStartDate; }

    public LocalDate getAcquisitionEndDate() { return acquisitionEndDate; }
    public void setAcquisitionEndDate(LocalDate acquisitionEndDate) { this.acquisitionEndDate = acquisitionEndDate; }

    public Integer getStcgThresholdDays() { return stcgThresholdDays; }
    public void setStcgThresholdDays(Integer stcgThresholdDays) { this.stcgThresholdDays = stcgThresholdDays; }

    public BigDecimal getEquityExposureMinPercent() { return equityExposureMinPercent; }
    public void setEquityExposureMinPercent(BigDecimal equityExposureMinPercent) { this.equityExposureMinPercent = equityExposureMinPercent; }

    public BigDecimal getStcgTaxRatePercent() { return stcgTaxRatePercent; }
    public void setStcgTaxRatePercent(BigDecimal stcgTaxRatePercent) { this.stcgTaxRatePercent = stcgTaxRatePercent; }

    public BigDecimal getLtcgTaxRatePercent() { return ltcgTaxRatePercent; }
    public void setLtcgTaxRatePercent(BigDecimal ltcgTaxRatePercent) { this.ltcgTaxRatePercent = ltcgTaxRatePercent; }

    public Boolean getIndexationAllowed() { return indexationAllowed; }
    public void setIndexationAllowed(Boolean indexationAllowed) { this.indexationAllowed = indexationAllowed; }

    public String getProvisionalNotice() { return provisionalNotice; }
    public void setProvisionalNotice(String provisionalNotice) { this.provisionalNotice = provisionalNotice; }

    public String getLegalInstrumentRef() { return legalInstrumentRef; }
    public void setLegalInstrumentRef(String legalInstrumentRef) { this.legalInstrumentRef = legalInstrumentRef; }

    public String getFinanceActRef() { return financeActRef; }
    public void setFinanceActRef(String financeActRef) { this.financeActRef = financeActRef; }

    public LocalDate getTransferStartDate() { return transferStartDate; }
    public void setTransferStartDate(LocalDate transferStartDate) { this.transferStartDate = transferStartDate; }

    public LocalDate getTransferEndDate() { return transferEndDate; }
    public void setTransferEndDate(LocalDate transferEndDate) { this.transferEndDate = transferEndDate; }

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

