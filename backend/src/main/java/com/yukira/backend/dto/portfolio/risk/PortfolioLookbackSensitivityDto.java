package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PortfolioLookbackSensitivityDto(
    String overallComparabilityState, // "DIRECTLY_COMPARABLE", "CONDITIONALLY_COMPARABLE", "NOT_DIRECTLY_COMPARABLE", "INSUFFICIENT_EVIDENCE"
    List<String> includedFundUniverse,
    List<BigDecimal> allocationWeights,
    List<PortfolioLookbackWindowResultDto> windows,
    List<VolatilitySensitivityComparisonDto> volatilityComparisons,
    List<CorrelationSensitivityComparisonDto> correlationComparisons,
    List<FundRiskContributionSensitivityDto> contributionComparisons,
    DiversificationSensitivityComparisonDto diversificationComparison,
    DownsideSensitivityComparisonDto downsideComparison,
    List<String> comparabilityLimitations,
    Map<String, String> methodologyNotes
) {}

