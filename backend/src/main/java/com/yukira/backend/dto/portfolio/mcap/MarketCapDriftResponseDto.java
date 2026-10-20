package com.yukira.backend.dto.portfolio.mcap;

import java.util.List;

/**
 * Complete investor response for Point-in-Time Market-Cap Style Drift & Allocation Decomposition.
 */
public record MarketCapDriftResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    String amfiCode,
    String schemeCode,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String status,
    String message,
    String methodologyVersion,
    String runId,
    String knowledgeCutoff,
    int totalSnapshotsFound,
    int eligibleSnapshotsCount,
    int skippedSnapshotsCount,
    List<MarketCapObservationDto> observations,
    List<MarketCapDriftChangeDto> transitions,
    List<MarketCapReclassificationItemDto> allReclassifiedHoldings,
    List<String> dataQualityWarnings,
    List<String> investigationQuestions,
    List<String> disclaimers
) {}
