package com.yukira.backend.dto.workspace;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record InvestorComparisonVersionDto(
    Long id,
    Long comparisonResearchId,
    Integer versionNumber,
    String schemeOptionIdsCsv,
    LocalDate asOfDate,
    String benchmarkCode,
    String compatibilityStatus,
    String metricsSummaryJson,
    String investorThesis,
    String investorNotes,
    String versionReason,
    ZonedDateTime createdAt
) {}

