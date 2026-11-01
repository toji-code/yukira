package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record PortfolioFundRiskContributionDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String planType,
    String category,
    BigDecimal marketValue,
    BigDecimal allocationWeightPct,
    BigDecimal standaloneVolatilityPct,
    BigDecimal marginalRiskContributionPct,
    BigDecimal componentRiskContributionPct,
    BigDecimal percentageRiskContribution,
    BigDecimal contributionToWeightRatio,
    boolean isNegativeContribution,
    boolean isRiskDriver,
    boolean isDiversificationDampener,
    String classification,
    String evidenceState,
    String standaloneVolatilityEvidenceState,
    String marginalRiskContributionEvidenceState,
    String componentRiskContributionEvidenceState,
    boolean isMathematicallyReliable,
    String interpretation
) {}
