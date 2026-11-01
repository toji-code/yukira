package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Durable, immutable allocation snapshot representing an investor's exact portfolio composition
 * at a specific point-in-time calculation (Tasks 105, 106).
 *
 * Epistemic Rules:
 * 1. Once created, a snapshot is immutable; subsequent portfolio edits create new snapshot instances.
 * 2. Unambiguously separates known valued capital from total portfolio capital.
 * 3. Never treats unvalued holdings as zero; tracks coverage percentage explicitly.
 */
@Entity
@Table(name = "investor_portfolio_allocation_snapshot")
public class InvestorPortfolioAllocationSnapshot implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, unique = true, length = 100)
    private String snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "valuation_as_of_date", nullable = false)
    private LocalDate valuationAsOfDate;

    @Column(name = "knowledge_cutoff", nullable = false)
    private OffsetDateTime knowledgeCutoff;

    @Column(name = "known_valued_capital", precision = 24, scale = 4)
    private BigDecimal knownValuedCapital;

    @Column(name = "total_portfolio_capital", precision = 24, scale = 4)
    private BigDecimal totalPortfolioCapital;

    @Column(name = "coverage_pct", precision = 10, scale = 4)
    private BigDecimal coveragePct;

    @Column(name = "valued_position_count", nullable = false)
    private Integer valuedPositionCount;

    @Column(name = "unvalued_position_count", nullable = false)
    private Integer unvaluedPositionCount;

    @Column(name = "total_position_count", nullable = false)
    private Integer totalPositionCount;

    @Column(name = "weight_calculation_convention", nullable = false, length = 100)
    private String weightCalculationConvention = "TOTAL_VALUED_CAPITAL";

    @Column(name = "weight_normalization_denominator", nullable = false, precision = 10, scale = 4)
    private BigDecimal weightNormalizationDenominator = BigDecimal.valueOf(100.0);

    @Column(name = "snapshot_fingerprint", nullable = false, length = 64)
    private String snapshotFingerprint;

    @Column(name = "is_immutable", nullable = false)
    private Boolean isImmutable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvestorPortfolioAllocationSnapshotItem> items = new ArrayList<>();

    public InvestorPortfolioAllocationSnapshot() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public LocalDate getValuationAsOfDate() { return valuationAsOfDate; }
    public void setValuationAsOfDate(LocalDate valuationAsOfDate) { this.valuationAsOfDate = valuationAsOfDate; }

    public OffsetDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(OffsetDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public BigDecimal getKnownValuedCapital() { return knownValuedCapital; }
    public void setKnownValuedCapital(BigDecimal knownValuedCapital) { this.knownValuedCapital = knownValuedCapital; }

    public BigDecimal getTotalPortfolioCapital() { return totalPortfolioCapital; }
    public void setTotalPortfolioCapital(BigDecimal totalPortfolioCapital) { this.totalPortfolioCapital = totalPortfolioCapital; }

    public BigDecimal getCoveragePct() { return coveragePct; }
    public void setCoveragePct(BigDecimal coveragePct) { this.coveragePct = coveragePct; }

    public Integer getValuedPositionCount() { return valuedPositionCount; }
    public void setValuedPositionCount(Integer valuedPositionCount) { this.valuedPositionCount = valuedPositionCount; }

    public Integer getUnvaluedPositionCount() { return unvaluedPositionCount; }
    public void setUnvaluedPositionCount(Integer unvaluedPositionCount) { this.unvaluedPositionCount = unvaluedPositionCount; }

    public Integer getTotalPositionCount() { return totalPositionCount; }
    public void setTotalPositionCount(Integer totalPositionCount) { this.totalPositionCount = totalPositionCount; }

    public String getWeightCalculationConvention() { return weightCalculationConvention; }
    public void setWeightCalculationConvention(String weightCalculationConvention) { this.weightCalculationConvention = weightCalculationConvention; }

    public BigDecimal getWeightNormalizationDenominator() { return weightNormalizationDenominator; }
    public void setWeightNormalizationDenominator(BigDecimal weightNormalizationDenominator) { this.weightNormalizationDenominator = weightNormalizationDenominator; }

    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }

    public Boolean getImmutable() { return isImmutable; }
    public void setImmutable(Boolean immutable) { isImmutable = immutable; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public List<InvestorPortfolioAllocationSnapshotItem> getItems() { return items; }
    public void setItems(List<InvestorPortfolioAllocationSnapshotItem> items) { this.items = items; }

    public void addItem(InvestorPortfolioAllocationSnapshotItem item) {
        items.add(item);
        item.setSnapshot(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorPortfolioAllocationSnapshot that)) return false;
        return Objects.equals(id, that.id) || Objects.equals(snapshotId, that.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId);
    }
}

