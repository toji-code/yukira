package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_holding")
public class PortfolioHolding implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_snapshot_id", nullable = false)
    private PortfolioSnapshot portfolioSnapshot;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "reported_weight", nullable = false, precision = 10, scale = 6)
    private BigDecimal reportedWeight;

    @Column(name = "market_value", precision = 24, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "quantity", precision = 24, scale = 4)
    private BigDecimal quantity;

    @Column(name = "holding_rank")
    private Integer holdingRank;

    public PortfolioHolding() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PortfolioSnapshot getPortfolioSnapshot() { return portfolioSnapshot; }
    public void setPortfolioSnapshot(PortfolioSnapshot portfolioSnapshot) { this.portfolioSnapshot = portfolioSnapshot; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public BigDecimal getReportedWeight() { return reportedWeight; }
    public void setReportedWeight(BigDecimal reportedWeight) { this.reportedWeight = reportedWeight; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Integer getHoldingRank() { return holdingRank; }
    public void setHoldingRank(Integer holdingRank) { this.holdingRank = holdingRank; }
}
