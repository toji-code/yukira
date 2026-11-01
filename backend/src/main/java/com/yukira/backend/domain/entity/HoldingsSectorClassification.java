package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "holdings_sector_classification")
public class HoldingsSectorClassification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_code", nullable = false, unique = true, length = 64)
    private String sectorCode;

    @Column(name = "sector_name", nullable = false, length = 128)
    private String sectorName;

    @Column(name = "macro_economic_sector", length = 128)
    private String macroEconomicSector;

    @Column(name = "industry_group", length = 128)
    private String industryGroup;

    @Column(name = "taxonomy_version", nullable = false, length = 32)
    private String taxonomyVersion = "SEBI_AMFI_2024";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public HoldingsSectorClassification() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (taxonomyVersion == null) taxonomyVersion = "SEBI_AMFI_2024";
        if (isActive == null) isActive = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSectorCode() { return sectorCode; }
    public void setSectorCode(String sectorCode) { this.sectorCode = sectorCode; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public String getMacroEconomicSector() { return macroEconomicSector; }
    public void setMacroEconomicSector(String macroEconomicSector) { this.macroEconomicSector = macroEconomicSector; }

    public String getIndustryGroup() { return industryGroup; }
    public void setIndustryGroup(String industryGroup) { this.industryGroup = industryGroup; }

    public String getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(String taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingsSectorClassification that)) return false;
        return Objects.equals(sectorCode, that.sectorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sectorCode);
    }
}

