package com.yukira.backend.dto.portfolio;

import java.util.List;

public record BenchmarkDivergenceResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    List<PeriodDivergenceSummaryDto> periods,
    List<ApparentChangeBreakdownDto> apparentChanges,
    String overallStatus, // "VERIFIED_PARTIAL", "UNVERIFIED_SOURCE", "INSUFFICIENT_DATA"
    String epistemicDisclaimer,
    String knowledgeCutoff
) {}
