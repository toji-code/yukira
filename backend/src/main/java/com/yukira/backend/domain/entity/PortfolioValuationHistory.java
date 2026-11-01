package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "portfolio_valuation_history")
public class PortfolioValuationHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "knowledge_cutoff")
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "total_market_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalMarketValue;

    @Column(name = "total_cost_basis", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCostBasis;

    @Column(name = "unrealized_gain_loss", precision = 19, scale = 4)
    private BigDecimal unrealizedGainLoss;

    @Column(name = "valued_positions_count", nullable = false)
    private Integer valuedPositionsCount = 0;

    @Column(name = "unvalued_positions_count", nullable = false)
    private Integer unvaluedPositionsCount = 0;

    @Column(name = "valuation_source_context", columnDefinition = "text")
    private String valuationSourceContext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public PortfolioValuationHistory() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (valuedPositionsCount == null) valuedPositionsCount = 0;
        if (unvaluedPositionsCount == null) unvaluedPositionsCount = 0;
        if (unrealizedGainLoss == null && totalMarketValue != null && totalCostBasis != null) {
            unrealizedGainLoss = totalMarketValue.subtract(totalCostBasis);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }

    public LocalDate getValuationDate() { return valuationDate; }
    public void setValuationDate(LocalDate valuationDate) { this.valuationDate = valuationDate; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public BigDecimal getTotalMarketValue() { return totalMarketValue; }
    public void setTotalMarketValue(BigDecimal totalMarketValue) { this.totalMarketValue = totalMarketValue; }

    public BigDecimal getTotalCostBasis() { return totalCostBasis; }
    public void setTotalCostBasis(BigDecimal totalCostBasis) { this.totalCostBasis = totalCostBasis; }

    public BigDecimal getUnrealizedGainLoss() { return unrealizedGainLoss; }
    public void setUnrealizedGainLoss(BigDecimal unrealizedGainLoss) { this.unrealizedGainLoss = unrealizedGainLoss; }

    public Integer getValuedPositionsCount() { return valuedPositionsCount; }
    public void setValuedPositionsCount(Integer valuedPositionsCount) { this.valuedPositionsCount = valuedPositionsCount; }

    public Integer getUnvaluedPositionsCount() { return unvaluedPositionsCount; }
    public void setUnvaluedPositionsCount(Integer unvaluedPositionsCount) { this.unvaluedPositionsCount = unvaluedPositionsCount; }

    public String getValuationSourceContext() { return valuationSourceContext; }
    public void setValuationSourceContext(String valuationSourceContext) { this.valuationSourceContext = valuationSourceContext; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioValuationHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

