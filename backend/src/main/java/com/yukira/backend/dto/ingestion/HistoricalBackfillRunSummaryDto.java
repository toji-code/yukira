package com.yukira.backend.dto.ingestion;

import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService.TargetedBackfillFundResult;

import java.time.OffsetDateTime;
import java.util.List;

public record HistoricalBackfillRunSummaryDto(
    String runId,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String triggerType,
    int requestedCohortSize,
    int actualCohortSize,
    int completedCount,
    int partialCount,
    int failedCount,
    int noDataCount,
    int shortHistoryCount,
    int alreadyCompleteCount,
    int totalObservationsInserted,
    int totalDuplicatesSkipped,
    int totalRevisionsCreated,
    int sourceRequestsMade,
    int sourceRequestsSkipped,
    int failedRequests,
    long durationMs,
    double throughputObsPerSec,
    List<TargetedBackfillFundResult> fundResults,
    long beforeAnyNav,
    long afterAnyNav,
    long before1Y,
    long after1Y,
    long before3Y,
    long after3Y,
    long before5Y,
    long after5Y,
    long newly3YReadyCount,
    long remaining3YGap,
    long newly5YReadyCount,
    long remaining5YGap,
    long downloadDurationMs,
    long parseDurationMs,
    long persistDurationMs,
    double optionsPerMin
) {
    public HistoricalBackfillRunSummaryDto(
        String runId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String triggerType,
        int requestedCohortSize,
        int actualCohortSize,
        int completedCount,
        int partialCount,
        int failedCount,
        int noDataCount,
        int shortHistoryCount,
        int alreadyCompleteCount,
        int totalObservationsInserted,
        int totalDuplicatesSkipped,
        int totalRevisionsCreated,
        int sourceRequestsMade,
        int sourceRequestsSkipped,
        int failedRequests,
        long durationMs,
        double throughputObsPerSec,
        List<TargetedBackfillFundResult> fundResults
    ) {
        this(
            runId, startedAt, completedAt, triggerType, requestedCohortSize, actualCohortSize,
            completedCount, partialCount, failedCount, noDataCount, shortHistoryCount, alreadyCompleteCount,
            totalObservationsInserted, totalDuplicatesSkipped, totalRevisionsCreated,
            sourceRequestsMade, sourceRequestsSkipped, failedRequests, durationMs, throughputObsPerSec, fundResults,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0.0
        );
    }
}
