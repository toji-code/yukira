package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Predefined or custom historical market stress episode metadata.
 */
public record HistoricalMarketEpisodeDto(
    String episodeId,
    String title,
    String category,
    LocalDate startDate,
    LocalDate endDate,
    String peakTroughConvention,
    String regimeType,
    String description,
    boolean isAvailable,
    String unavailabilityReason,
    Integer tradingDaysCount,
    BigDecimal benchmarkTotalReturnPct,
    String benchmarkCode
) {}

