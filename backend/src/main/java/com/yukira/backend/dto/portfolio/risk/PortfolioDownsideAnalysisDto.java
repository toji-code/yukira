package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioDownsideAnalysisDto(
    BigDecimal maximumDrawdownPct,
    LocalDate maxDrawdownPeakDate,
    LocalDate maxDrawdownTroughDate,
    int drawdownDurationTradingDays,
    int recoveryTradingDays,
    boolean hasRecovered,
    String drawdownEvidenceState,
    String drawdownLimitationExplanation,
    BigDecimal negativeReturnFrequencyPct,
    BigDecimal historicalVaR95Pct,
    BigDecimal historicalExpectedShortfall95Pct,
    BigDecimal varConfidenceLevel,
    int effectiveTailObservationCount,
    int requiredMinimumTailObservations,
    String varQuantileConvention,
    String esAveragingConvention,
    String varEvidenceState,
    String expectedShortfallEvidenceState,
    int observationCount,
    String metricGovernanceStatus,
    String tailSampleLimitations
) {}
