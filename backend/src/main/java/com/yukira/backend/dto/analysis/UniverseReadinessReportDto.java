package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record UniverseReadinessReportDto(
    LocalDate asOfDate,
    long totalCatalog,
    long activeUniverse,
    long activeWithNav,
    long activeZeroNav,
    long active1yReady,
    long active3yReady,
    long active5yReady,
    long activeWithBenchmarkMapping,
    long explicitBenchmarkMappedCount,
    long fallbackBenchmarkCount,
    long unmappedBenchmarkCount,
    long activeWithValidBenchmarkHistory,
    long activeWithRiskFreeHistory,
    long active3yPitPairReady,
    long active3yQuantReady,
    long active3yScoreReady,
    Map<String, BlockerStat> blockerClassification,
    List<BenchmarkAuditItem> benchmarkAudits,
    RiskFreeAuditItem riskFreeAudit,
    List<FunnelStepItem> quantReadinessFunnel,
    AuditPerformanceMetrics performanceMetrics
) {
    public record BlockerStat(
        String category,
        long count,
        double percentageOfTargetCohort,
        double percentageOfActiveUniverse,
        List<String> representativeExamples
    ) {}

    public record BenchmarkAuditItem(
        Long benchmarkId,
        String code,
        String name,
        String provider,
        long observationCount,
        LocalDate minDate,
        LocalDate maxDate,
        long gapCount,
        long duplicateCount,
        long invalidCount,
        boolean is1yReady,
        boolean is3yReady,
        long mappedActiveSchemeCount,
        long paired3yDateCount
    ) {}

    public record RiskFreeAuditItem(
        String code,
        long observationCount,
        LocalDate minDate,
        LocalDate maxDate,
        long gapCount,
        boolean is3yReady
    ) {}

    public record FunnelStepItem(
        String stepName,
        long count,
        double percentageOfActiveUniverse,
        long excludedCount,
        String primaryExclusionReason
    ) {}

    public record AuditPerformanceMetrics(
        long optionsScanned,
        long queryCount,
        long durationMs,
        long peakMemoryBytes
    ) {}
}
