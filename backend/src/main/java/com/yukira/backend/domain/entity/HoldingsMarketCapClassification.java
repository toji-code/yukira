package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "holdings_market_cap_classification")
public class HoldingsMarketCapClassification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isin", nullable = false, length = 32)
    private String isin;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "market_cap_rank")
    private Integer marketCapRank;

    @Column(name = "market_cap_category", nullable = false, length = 32)
    private String marketCapCategory; // LARGE_CAP, MID_CAP, SMALL_CAP, MICRO_CAP

    @Column(name = "effective_period_start", nullable = false)
    private LocalDate effectivePeriodStart;

    @Column(name = "effective_period_end", nullable = false)
    private LocalDate effectivePeriodEnd;

    @Column(name = "source_provenance")
    private String sourceProvenance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public HoldingsMarketCapClassification() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public Integer getMarketCapRank() { return marketCapRank; }
    public void setMarketCapRank(Integer marketCapRank) { this.marketCapRank = marketCapRank; }

    public String getMarketCapCategory() { return marketCapCategory; }
    public void setMarketCapCategory(String marketCapCategory) { this.marketCapCategory = marketCapCategory; }

    public LocalDate getEffectivePeriodStart() { return effectivePeriodStart; }
    public void setEffectivePeriodStart(LocalDate effectivePeriodStart) { this.effectivePeriodStart = effectivePeriodStart; }

    public LocalDate getEffectivePeriodEnd() { return effectivePeriodEnd; }
    public void setEffectivePeriodEnd(LocalDate effectivePeriodEnd) { this.effectivePeriodEnd = effectivePeriodEnd; }

    public String getSourceProvenance() { return sourceProvenance; }
    public void setSourceProvenance(String sourceProvenance) { this.sourceProvenance = sourceProvenance; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingsMarketCapClassification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

