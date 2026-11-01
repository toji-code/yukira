package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Authoritative Point-in-Time Benchmark Observation Series Data Contract:
 *
 * Provides downstream quantitative and presentation tiers with an immutable, verifiable,
 * bitemporally-filtered benchmark TRI observation series.
 *
 * Epistemic Guardrail:
 * Every series is strictly bounded by (effectiveDate <= analysisCutoffDate)
 * AND (availabilityTime <= knowledgeCutoffTime).
 */
public record BenchmarkObservationSeriesDto(
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String mappingStatus,
    LocalDate startDate,
    LocalDate analysisCutoffDate,
    OffsetDateTime knowledgeCutoffTime,
    int totalObservations,
    LocalDate firstObservationDate,
    LocalDate latestObservationDate,
    BigDecimal firstTriValue,
    BigDecimal latestTriValue,
    List<BenchmarkObservationItem> observations
) {
    public record BenchmarkObservationItem(
        Long observationId,
        LocalDate effectiveDate,
        BigDecimal triValue,
        int revisionSeq,
        boolean isLatestRevision,
        OffsetDateTime availabilityTime,
        String sourceArtifactSha256
    ) {}
}
