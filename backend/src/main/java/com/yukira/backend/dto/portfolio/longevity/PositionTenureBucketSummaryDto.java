package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary of a portfolio tenure bucket (e.g. < 6 Months, 6-12 Months, 1-3 Years, > 3 Years).
 */
public record PositionTenureBucketSummaryDto(
    String bucketKey,
    String bucketLabel,
    int holdingsCount,
    BigDecimal totalReportedWeightPct,
    BigDecimal portfolioWeightSharePct,
    List<String> topHoldings
) {}
