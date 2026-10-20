package com.yukira.backend.dto.portfolio.mcap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reported exposure and holding details for a specific market-cap category or asset class bucket.
 *
 * Epistemic Rules:
 * 1. reportedWeightPct preserves raw disclosed portfolio weight sum (never silently normalized).
 * 2. normalizedWeightPct provides explicit optional secondary normalization relative to total disclosed weights.
 * 3. Market-cap categories (LARGE_CAP, MID_CAP, SMALL_CAP) apply strictly to equity securities.
 *    Non-equity exposures (CASH, DEBT_OTHER) and UNCLASSIFIED holdings are kept strictly distinct.
 */
public record MarketCapBucketExposureDto(
    String category,
    String categoryLabel,
    BigDecimal reportedWeightPct,
    BigDecimal normalizedWeightPct,
    int holdingCount,
    List<String> topHoldings,
    String classificationSource,
    String rankingPeriod,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String provenance
) {}
