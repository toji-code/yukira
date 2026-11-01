package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioValuationBasisDto(
    BigDecimal totalPortfolioMarketValue,
    BigDecimal totalInvestedAmount,
    BigDecimal totalGainLoss,
    BigDecimal totalGainLossPercentage,
    int totalPositionCount,
    int valuedPositionCount,
    int unvaluedPositionCount,
    BigDecimal reliableValuationPercentage,
    int distinctSchemesCount,
    int distinctAmcsCount,
    String allocationMode,
    String valuationNotice,
    List<PortfolioPositionAllocationDto> positions
) {
    public int unvaluedPositionsCount() { return unvaluedPositionCount; }
    public int totalPositionsCount() { return totalPositionCount; }
    public int knownValuedPositionsCount() { return valuedPositionCount; }
    public double valuationCoveragePercentage() {
        return reliableValuationPercentage != null ? reliableValuationPercentage.doubleValue() : 0.0;
    }
}
