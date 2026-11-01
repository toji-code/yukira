package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request payload for saving a comparison investigation into the investor workspace (Task 155).
 */
public record SaveComparisonResearchRequest(
    String comparisonTitle,
    List<Long> schemeOptionIds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode,
    String compatibilityStatus,
    String metricsSummaryJson,
    String investorThesis,
    String investorNotes,
    String associatedFindingId
) {}
