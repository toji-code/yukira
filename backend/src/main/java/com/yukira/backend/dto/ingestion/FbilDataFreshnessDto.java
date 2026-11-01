package com.yukira.backend.dto.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Data freshness DTO for FBIL risk-free rate observations (TSK-M1-04 / Phase A2).
 */
public record FbilDataFreshnessDto(
    String benchmarkCode,
    String benchmarkName,
    String provider,
    String daycountConvention,
    String freshnessState,
    LocalDate earliestObservationDate,
    LocalDate latestObservationDate,
    OffsetDateTime latestRetrievalTimestamp,
    OffsetDateTime latestAvailabilityTimestamp,
    String latestSourceHash,
    long totalObservationCount,
    Long daysSinceLatestObservation,
    int freshnessThresholdDays,
    String governanceDisclaimer,
    String coverageStatus,
    String explanation
) {
    public FbilDataFreshnessDto(
        String benchmarkCode,
        String benchmarkName,
        String provider,
        String daycountConvention,
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
    ) {
        this(
            benchmarkCode, benchmarkName, provider, daycountConvention, freshnessState,
            earliestObservationDate, latestObservationDate, latestRetrievalTimestamp,
            latestAvailabilityTimestamp, latestSourceHash, totalObservationCount,
            daysSinceLatestObservation, freshnessThresholdDays, governanceDisclaimer,
            totalObservationCount > 0 ? "OBSERVATIONS_AVAILABLE" : "NO_OBSERVATIONS",
            totalObservationCount > 0
                ? "FBIL 91D T-Bill has " + totalObservationCount + " observations recorded (latest: " + latestObservationDate + ")."
                : "No FBIL risk-free observations currently available in database."
        );
    }
}

