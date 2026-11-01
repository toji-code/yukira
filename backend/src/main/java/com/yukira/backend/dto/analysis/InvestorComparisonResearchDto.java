package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO representing a persisted investor comparison research investigation (Task 155).
 */
public record InvestorComparisonResearchDto(
    Long id,
    Long investorId,
    String comparisonTitle,
    List<Long> schemeOptionIds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode,
    String compatibilityStatus,
    String metricsSummaryJson,
    String investorThesis,
    String investorNotes,
    String associatedFindingId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
