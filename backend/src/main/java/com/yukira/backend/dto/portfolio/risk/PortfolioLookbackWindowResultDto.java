package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioLookbackWindowResultDto(
    String windowCode, // "1Y", "3Y", "5Y"
    int requestedObservationCount, // 250, 750, 1250
    int actualObservationCount,
    LocalDate observationStartDate,
    LocalDate observationEndDate,
    String windowEvidenceState,
    String matrixSuitabilityState,
    String matrixEvidenceState,
    BigDecimal minimumEigenvalue,
    BigDecimal conditionNumber,
    boolean isPositiveSemiDefinite,
    boolean isNearSingular,
    BigDecimal annualizedPortfolioVolatilityProxy,
    String portfolioVolatilityEvidenceState,
    BigDecimal weightedStandaloneVolatilityPct,
    BigDecimal diversificationEffectPct,
    BigDecimal diversificationRatio,
    String diversificationEvidenceState,
    BigDecimal maximumDrawdownPct,
    int drawdownDurationTradingDays,
    boolean hasRecovered,
    String drawdownEvidenceState,
    BigDecimal historicalVaR95Pct,
    BigDecimal historicalExpectedShortfall95Pct,
    int effectiveTailObservationCount,
    String varEvidenceState,
    String expectedShortfallEvidenceState,
    List<PortfolioFundRiskContributionDto> fundRiskContributions,
    List<List<BigDecimal>> correlationMatrix,
    List<String> fundNames,
    List<Long> fundIds,
    String status, // "AVAILABLE", "LIMITED_EVIDENCE", "INSUFFICIENT_EVIDENCE", "UNAVAILABLE"
    String limitationExplanation
) {}

