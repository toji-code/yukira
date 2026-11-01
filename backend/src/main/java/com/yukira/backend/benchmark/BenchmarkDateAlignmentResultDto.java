package com.yukira.backend.benchmark;

import com.yukira.backend.universe.EquityUniverseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result DTO representing date-aligned multi-asset calendar synchronization and
 * data-quality diagnostics between fund NAV observations and canonical benchmark series (TSK-M1-04).
 */
public record BenchmarkDateAlignmentResultDto(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    String schemeCategory,
    String schemeSubcategory,
    EquityUniverseCategory equityUniverseCategory,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String benchmarkProvider,
    String returnVariant,
    @com.fasterxml.jackson.annotation.JsonProperty("isTri") boolean isTri,
    String mappingStatus,
    String methodologyStatus,
    String mappingRationalization,
    LocalDate requestedStartDate,
    LocalDate requestedEndDate,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    LocalDate earliestAlignedDate,
    LocalDate latestAlignedDate,
    int fundObservationsInWindow,
    int fundDistinctValidDates,
    int benchmarkObservationsInWindow,
    int benchmarkDistinctValidDates,
    int alignedTradingDays,
    int fundDatesWithoutBenchmarkCount,
    int benchmarkDatesWithoutFundCount,
    int downMarketBenchmarkDays,
    int duplicateOrInvalidObservationsExcluded,
    BigDecimal overlapRatio,
    String alignmentStatus,
    List<LocalDate> sampleFundDatesWithoutBenchmark,
    List<LocalDate> sampleBenchmarkDatesWithoutFund,
    List<String> sourceProvenanceLimitations,
    Map<String, MetricReadinessStatusDto> metricReadiness,
    String governanceDisclaimer
) {}
