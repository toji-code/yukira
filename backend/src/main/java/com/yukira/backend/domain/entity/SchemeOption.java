package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "scheme_option")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SchemeOption implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SchemePlan plan;

    @Column(name = "option_type", nullable = false, length = 30)
    private String optionType; // GROWTH, IDCW_PAYOUT, IDCW_REINVESTMENT

    @Column(name = "amfi_code", unique = true, length = 50)
    private String amfiCode;

    @Column(name = "isin", unique = true, length = 20)
    private String isin;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Transient
    private com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore;

    @Transient
    private String latestNavDate;

    @Transient
    private Long navObservationCount;

    @Transient
    private String latestHoldingsDate;

    @Transient
    private Integer holdingsCount;

    @Transient
    private String navFreshnessState;

    @Transient
    private String navFreshnessExplanation;

    @Transient
    private String holdingsFreshnessState;

    @Transient
    private String holdingsFreshnessExplanation;

    @Transient
    private Double holdingsRawSumPercentage;

    @Transient
    private Integer snapshotCount;

    @Transient
    private Boolean inReferenceUniverse;

    @Transient
    private String universeCategory;

    @Transient
    private String universeExclusionReason;

    @Transient
    private String historySufficiencyStatus;

    @Transient
    private Long distinctObservationCount;

    @Transient
    private String earliestNavDate;

    public SchemeOption() {}

    public SchemeOption(SchemePlan plan, String optionType, String amfiCode, String isin) {
        this.plan = plan;
        this.optionType = optionType;
        this.amfiCode = amfiCode;
        this.isin = isin;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemePlan getPlan() { return plan; }
    public SchemePlan getSchemePlan() { return plan; }
    public void setPlan(SchemePlan plan) { this.plan = plan; }

    public Scheme getScheme() {
        return plan != null ? plan.getScheme() : null;
    }

    public String getOptionType() { return optionType; }
    public String getOptionName() { return plan != null ? plan.getPlanName() + " " + optionType : optionType; }
    public String getSchemeCode() { return getScheme() != null ? getScheme().getCode() : null; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public String getAmfiCode() { return amfiCode; }
    public String getAmfiSchemeCode() { return amfiCode; }
    public void setAmfiCode(String amfiCode) { this.amfiCode = amfiCode; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public com.yukira.backend.scoring.dto.YukiraScoreSummary getYukiraScore() { return yukiraScore; }
    public void setYukiraScore(com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore) { this.yukiraScore = yukiraScore; }

    public String getLatestNavDate() { return latestNavDate; }
    public void setLatestNavDate(String latestNavDate) { this.latestNavDate = latestNavDate; }

    public Long getNavObservationCount() { return navObservationCount; }
    public void setNavObservationCount(Long navObservationCount) { this.navObservationCount = navObservationCount; }

    public String getLatestHoldingsDate() { return latestHoldingsDate; }
    public void setLatestHoldingsDate(String latestHoldingsDate) { this.latestHoldingsDate = latestHoldingsDate; }

    public Integer getHoldingsCount() { return holdingsCount; }
    public void setHoldingsCount(Integer holdingsCount) { this.holdingsCount = holdingsCount; }

    public String getNavFreshnessState() { return navFreshnessState; }
    public void setNavFreshnessState(String navFreshnessState) { this.navFreshnessState = navFreshnessState; }

    public String getNavFreshnessExplanation() { return navFreshnessExplanation; }
    public void setNavFreshnessExplanation(String navFreshnessExplanation) { this.navFreshnessExplanation = navFreshnessExplanation; }

    public String getHoldingsFreshnessState() { return holdingsFreshnessState; }
    public void setHoldingsFreshnessState(String holdingsFreshnessState) { this.holdingsFreshnessState = holdingsFreshnessState; }

    public String getHoldingsFreshnessExplanation() { return holdingsFreshnessExplanation; }
    public void setHoldingsFreshnessExplanation(String holdingsFreshnessExplanation) { this.holdingsFreshnessExplanation = holdingsFreshnessExplanation; }

    public Double getHoldingsRawSumPercentage() { return holdingsRawSumPercentage; }
    public void setHoldingsRawSumPercentage(Double holdingsRawSumPercentage) { this.holdingsRawSumPercentage = holdingsRawSumPercentage; }

    public Integer getSnapshotCount() { return snapshotCount; }
    public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }

    public Boolean getInReferenceUniverse() { return inReferenceUniverse; }
    public void setInReferenceUniverse(Boolean inReferenceUniverse) { this.inReferenceUniverse = inReferenceUniverse; }

    public String getUniverseCategory() { return universeCategory; }
    public void setUniverseCategory(String universeCategory) { this.universeCategory = universeCategory; }

    public String getUniverseExclusionReason() { return universeExclusionReason; }
    public void setUniverseExclusionReason(String universeExclusionReason) { this.universeExclusionReason = universeExclusionReason; }

    public String getHistorySufficiencyStatus() { return historySufficiencyStatus; }
    public void setHistorySufficiencyStatus(String historySufficiencyStatus) { this.historySufficiencyStatus = historySufficiencyStatus; }

    public Long getDistinctObservationCount() { return distinctObservationCount; }
    public void setDistinctObservationCount(Long distinctObservationCount) { this.distinctObservationCount = distinctObservationCount; }

    public String getEarliestNavDate() { return earliestNavDate; }
    public void setEarliestNavDate(String earliestNavDate) { this.earliestNavDate = earliestNavDate; }
}
