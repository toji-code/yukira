package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PortfolioRiskSummaryDto(
    BigDecimal totalKnownPortfolioValue,
    int valuedFundCount,
    int unvaluedPositionCount,
    BigDecimal knownValueAllocationCoveragePct,
    String weightDenominator,
    List<UnvaluedPositionDto> unvaluedPositions,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    LocalDate commonStartDate,
    LocalDate commonEndDate,
    int alignedObservationCount,
    String evidenceState,
    String windowEvidenceState,
    String portfolioVolatilityEvidenceState,
    int excludedObservationCount,
    int calendarGapExclusionsCount,
    boolean hasSynchronousDatesAcrossAllFunds,
    Map<String, Integer> perFundObservationCounts,
    String alignmentLimitations,
    BigDecimal annualizedPortfolioVolatilityProxy,
    int annualizationFactor,
    String annualizationConvention,
    String sampleDenominatorConvention,
    BigDecimal eulerReconciliationDiscrepancy,
    boolean isEulerReconciled,
    String eulerEvidenceState,
    String methodologyDisclaimer
) {}
