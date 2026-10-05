package com.yukira.backend.dto.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BenchmarkDataFreshnessDto(
    String benchmarkCode,
    String benchmarkName,
    String provider,
    String freshnessState,
    LocalDate earliestObservationDate,
    LocalDate latestObservationDate,
    OffsetDateTime latestRetrievalTimestamp,
    OffsetDateTime latestAvailabilityTimestamp,
    String latestSourceHash,
    long totalObservationCount,
    Long daysSinceLatestObservation,
    int freshnessThresholdDays,
    String governanceDisclaimer
) {}
