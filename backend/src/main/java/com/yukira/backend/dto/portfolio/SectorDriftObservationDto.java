package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Single historical observation of fund sector exposures, matched benchmark snapshot, and comparison state.
 */
public record SectorDriftObservationDto(
    Long portfolioSnapshotId,
    LocalDate portfolioDate,
    OffsetDateTime portfolioAvailabilityTime,
    String portfolioDisclosureScope,
    BigDecimal sumFundWeightsPct,
    int fundHoldingsCount,
    Long benchmarkId,
    String benchmarkCode,
    LocalDate benchmarkDate,
    OffsetDateTime benchmarkAvailabilityTime,
    BigDecimal benchmarkWeightCoveragePct,
    int benchmarkConstituentsCount,
    int benchmarkWeightedCount,
    long snapshotDateDiffDays,
    boolean isTemporallyComparable,
    String observationStatus,
    String statusReason,
    List<FundSectorExposureDto> sectors,
    List<BenchmarkSectorExposureDto> benchmarkSectors,
    List<SectorActiveComparisonDto> sectorComparisons,
    BigDecimal fundClassificationCoveragePct,
    String sourceArtifactUri,
    String sourceArtifactSha256,
    List<FundIndustryExposureDto> industries,
    List<IndustryActiveComparisonDto> industryComparisons,
    List<SecurityExposureWaterfallDto> securityWaterfall
) {}

