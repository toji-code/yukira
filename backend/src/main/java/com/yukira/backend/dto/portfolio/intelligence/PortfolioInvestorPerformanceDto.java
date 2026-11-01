package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioInvestorPerformanceDto(
    String moneyWeightedReturnStatus,
    String explanation,
    List<FundNavReturnSummaryDto> fundNavHistoricalReturns,
    BigDecimal portfolioXirrAnnualized,
    Integer cashFlowCount,
    BigDecimal totalInvestedCapital,
    BigDecimal totalRealizedGain
) {
    public PortfolioInvestorPerformanceDto(
        String moneyWeightedReturnStatus,
        String explanation,
        List<FundNavReturnSummaryDto> fundNavHistoricalReturns
    ) {
        this(moneyWeightedReturnStatus, explanation, fundNavHistoricalReturns, null, 0, null, null);
    }
}
