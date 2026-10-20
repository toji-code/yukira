package com.yukira.backend.dto.portfolio.mcap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Benchmark comparison for market-cap style analysis, subject to strict completeness gating.
 *
 * Epistemic Rules:
 * 1. Benchmark constituent weights are never assumed to be zero when missing.
 * 2. If weight coverage < 99.50%, definitive active weights are GATED.
 * 3. Disclosed weights are explicitly labeled as partial lower bounds.
 */
public record MarketCapBenchmarkComparisonDto(
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    LocalDate benchmarkDate,
    OffsetDateTime benchmarkAvailabilityTime,
    int benchmarkConstituentsCount,
    int weightedConstituentsCount,
    BigDecimal benchmarkWeightCoveragePct,
    boolean isWeightCoverageComplete,
    BigDecimal benchmarkLargeCapDisclosedWeightPct,
    BigDecimal benchmarkMidCapDisclosedWeightPct,
    BigDecimal benchmarkSmallCapDisclosedWeightPct,
    String gatingStatus,
    String gatingReason,
    String disclaimer
) {}
