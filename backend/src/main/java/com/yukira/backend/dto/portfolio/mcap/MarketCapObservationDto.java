package com.yukira.backend.dto.portfolio.mcap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Observed market-cap allocation for a single eligible point-in-time portfolio snapshot.
 */
public record MarketCapObservationDto(
    Long snapshotId,
    LocalDate portfolioDate,
    OffsetDateTime availabilityTime,
    String portfolioDisclosureScope,
    BigDecimal sumReportedWeightsPct,
    BigDecimal weightReconciliationDeltaPct,
    int totalHoldingsCount,
    BigDecimal classificationCoverageCountPct,
    BigDecimal classificationCoverageWeightPct,
    BigDecimal unclassifiedWeightPct,
    int unclassifiedHoldingCount,
    String sourceRankingPeriod,
    LocalDate sourcePublicationDate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Long classificationSourceArtifactId,
    String classificationSourceArtifactSha256,
    String classificationStatus,
    List<MarketCapBucketExposureDto> bucketExposures,
    MarketCapBenchmarkComparisonDto benchmarkComparison
) {}
