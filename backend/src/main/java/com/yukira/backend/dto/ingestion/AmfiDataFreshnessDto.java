package com.yukira.backend.dto.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AmfiDataFreshnessDto(
    String freshnessState,
    LocalDate latestNavDate,
    OffsetDateTime latestRetrievalTimestamp,
    OffsetDateTime latestIngestionTimestamp,
    String latestSourceHash,
    long latestObservationsIngested,
    long latestRevisionsDetected,
    long latestDuplicatesSkipped,
    long totalNavObservationCount,
    long validationIssueCount,
    Long daysSinceLatestObservation,
    int freshnessThresholdDays,
    String governanceDisclaimer
) {}
