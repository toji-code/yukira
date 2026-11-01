package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Constituent position membership within an immutable portfolio allocation snapshot (Task 105, 106).
 */
@Entity
@Table(name = "investor_portfolio_allocation_snapshot_item")
public class InvestorPortfolioAllocationSnapshotItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private InvestorPortfolioAllocationSnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "scheme_code", length = 100)
    private String schemeCode;

    @Column(name = "scheme_name", length = 255)
    private String schemeName;

    @Column(name = "units", precision = 19, scale = 4)
    private BigDecimal units;

    @Column(name = "nav_price", precision = 19, scale = 4)
    private BigDecimal navPrice;

    @Column(name = "market_value", precision = 24, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "allocation_weight", precision = 10, scale = 6)
    private BigDecimal allocationWeight;

    @Column(name = "is_valued", nullable = false)
    private Boolean isValued = true;

    @Column(name = "valuation_status", nullable = false, length = 50)
    private String valuationStatus = "VALUED"; // VALUED, UNVALUED_MISSING_PRICE, UNVALUED_ZERO_UNITS

    @Column(name = "valuation_source_ref", length = 255)
    private String valuationSourceRef;

    public InvestorPortfolioAllocationSnapshotItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorPortfolioAllocationSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(InvestorPortfolioAllocationSnapshot snapshot) { this.snapshot = snapshot; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public String getSchemeCode() { return schemeCode; }
    public void setSchemeCode(String schemeCode) { this.schemeCode = schemeCode; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getNavPrice() { return navPrice; }
    public void setNavPrice(BigDecimal navPrice) { this.navPrice = navPrice; }

    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }

    public BigDecimal getAllocationWeight() { return allocationWeight; }
    public void setAllocationWeight(BigDecimal allocationWeight) { this.allocationWeight = allocationWeight; }

    public Boolean getValued() { return isValued; }
    public void setValued(Boolean valued) { isValued = valued; }

    public String getValuationStatus() { return valuationStatus; }
    public void setValuationStatus(String valuationStatus) { this.valuationStatus = valuationStatus; }

    public String getValuationSourceRef() { return valuationSourceRef; }
    public void setValuationSourceRef(String valuationSourceRef) { this.valuationSourceRef = valuationSourceRef; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorPortfolioAllocationSnapshotItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

