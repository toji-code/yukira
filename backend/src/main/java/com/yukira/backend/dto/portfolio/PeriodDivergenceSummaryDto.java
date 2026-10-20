package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodDivergenceSummaryDto(
    LocalDate portfolioDate,
    LocalDate benchmarkDate,
    long snapshotDateDifferenceDays,
    boolean snapshotDateCompatible, // true if difference <= 45 days
    int fundHoldingsCount,
    BigDecimal sumFundWeightsPct,
    int benchmarkConstituentCount,
    String weightCoverageStatus, // "TOP_WEIGHTS_DISCLOSED", "MEMBERSHIP_ONLY"
    BigDecimal sumDisclosedBenchmarkWeightsPct,
    int commonCount,
    int fundOnlyCount,
    int benchmarkOnlyCount,
    BigDecimal membershipInBenchmarkPct, // (commonCount / fundHoldingsCount) * 100
    BigDecimal offBenchmarkCapitalAllocationPct, // sum of fund weights of fund-only stocks
    BigDecimal verifiedActiveSharePct, // Active share computed ONLY on verified disclosed weights
    // Fund Market Cap exposures
    BigDecimal fundLargeCapPct,
    BigDecimal fundMidCapPct,
    BigDecimal fundSmallCapPct,
    BigDecimal fundUnclassifiedPct,
    // Benchmark Market Cap exposures
    BigDecimal benchmarkLargeCapPct,
    BigDecimal benchmarkMidCapPct,
    BigDecimal benchmarkSmallCapPct,
    String periodStatus, // "VERIFIED_PARTIAL", "UNVERIFIED_SOURCE", "INSUFFICIENT_DATA"
    String warningMessage
) {}
