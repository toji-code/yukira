package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard institutional performance periods response DTO (Task 132).
 * Strictly computes point-in-time boundary returns across 1Y, 3Y, 5Y, 10Y, and Since Inception.
 */
public record PerformancePeriodsResponseDto(
    Long schemeOptionId,
    String schemeName,
    String benchmarkCode,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    List<PerformancePeriodItem> periods,
    String governanceNotice
) {

    public record PerformancePeriodItem(
        String periodKey,              // "1Y", "3Y", "5Y", "10Y", "INCEPTION"
        String periodLabel,            // "1-Year", "3-Year", "5-Year", "10-Year", "Since Inception"
        LocalDate targetStartDate,
        LocalDate targetEndDate,
        LocalDate effectiveStartDate,  // Boundary date resolved via 4-day lookback window
        LocalDate effectiveEndDate,
        BigDecimal startNav,
        BigDecimal endNav,
        BigDecimal simpleReturn,       // RET-02 (e.g. 0.1523 for +15.23%)
        BigDecimal annualizedReturn,   // RET-03 / CAGR (for periods >= 1Y)
        BigDecimal benchmarkReturn,
        BigDecimal excessReturn,
        Integer tradingDaysCount,
        Integer startNavLookbackDays,
        Integer endNavLookbackDays,
        String eligibilityStatus,      // "ELIGIBLE", "INSUFFICIENT_HISTORY", "DATA_STALE"
        String statusReason
    ) {}
}
