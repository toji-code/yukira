package com.yukira.backend.scoring.population;

import java.time.LocalDate;
import java.util.List;

/**
 * Machine-readable report summarizing bounded historical NAV batch ingestion
 * for an entire peer candidate cohort.
 */
public record PeerCohortIngestionReport(
    String runId,
    String cohortId,
    LocalDate startDate,
    LocalDate endDate,
    int requestedFundsCount,
    int successfulFundsCount,
    int partialFundsCount,
    int zeroFundsCount,
    int failedFundsCount,
    long totalObservationsIngested,
    long totalDuplicatesSkipped,
    List<PeerFundCoverageSummary> fundCoverages,
    List<String> messages
) {}
