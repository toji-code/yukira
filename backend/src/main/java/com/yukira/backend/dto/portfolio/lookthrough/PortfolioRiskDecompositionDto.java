package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Institutional portfolio risk decomposition and factor exposure profile.
 * Distinguishes synthesized realized portfolio risk, weighted-average constituent risk,
 * realized diversification benefit, and empirical factor gates.
 */
public record PortfolioRiskDecompositionDto(
    String evaluationStatus,
    String benchmarkCode,
    String benchmarkName,
    LocalDate returnStartDate,
    LocalDate returnEndDate,
    int alignedTradingDaysCount,
    BigDecimal portfolioRealizedVolatilityPct,
    BigDecimal weightedAverageFundVolatilityPct,
    BigDecimal diversificationBenefitPct,
    BigDecimal portfolioMaxDrawdownPct,
    BigDecimal portfolioDownsideDeviationPct,
    BigDecimal portfolioBeta,
    BigDecimal benchmarkRealizedVolatilityPct,
    BigDecimal benchmarkMaxDrawdownPct,
    List<ConstituentFundRiskProfileDto> constituentRiskProfiles,
    List<PairwiseCorrelationDto> pairwiseCorrelations,
    List<FactorExposureItemDto> factorExposures,
    String rebalancingConvention,
    List<String> riskObservations,
    List<String> dataLimitations
) {}
