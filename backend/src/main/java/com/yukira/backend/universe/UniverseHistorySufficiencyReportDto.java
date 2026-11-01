package com.yukira.backend.universe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable report summarizing historical NAV sufficiency across the reference universe (TSK-M1-03).
 */
public record UniverseHistorySufficiencyReportDto(
    String ruleVersion,
    String countingUnit,
    int requiredThreshold,
    long totalReferenceOptionsEvaluated,
    long eligibleCount,
    long insufficientHistoryCount,
    long noHistoryCount,
    long dataQualityLimitedCount,
    Map<String, Long> countByStatus,
    Map<String, Long> eligibleByCategory,
    OffsetDateTime evaluatedAt,
    List<String> metricLimitations,
    List<String> governanceNotes
) {}
