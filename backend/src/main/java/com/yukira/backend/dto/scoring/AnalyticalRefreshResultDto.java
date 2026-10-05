package com.yukira.backend.dto.scoring;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalyticalRefreshResultDto(
    String refreshStatus,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    String dataFreshnessState,
    LocalDate latestNavDate,
    String benchmarkCode,
    boolean benchmarkDataAvailable,
    LocalDate latestBenchmarkDate,
    long totalFundsProcessed,
    long scoresGenerated,
    long scoresCached,
    long scoresAvailable,
    long scoresPartial,
    long scoresDataQualityLimited,
    long scoresUnavailable,
    long scoresNotApplicable,
    String summary,
    List<String> messages,
    String disclaimer
) {}
