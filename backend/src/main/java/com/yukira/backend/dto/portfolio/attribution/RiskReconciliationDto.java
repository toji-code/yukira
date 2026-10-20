package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;

public record RiskReconciliationDto(
    BigDecimal portfolioVolatilityPct,
    BigDecimal sumRiskContributionsPct,
    BigDecimal reconciliationDifferencePct,
    BigDecimal sumPercentageContributions,
    boolean isReconciled,
    String reconciliationStatus,
    String mathematicalIdentityFormula
) {}
