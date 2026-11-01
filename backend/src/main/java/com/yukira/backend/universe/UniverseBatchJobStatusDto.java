package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Machine-readable progress and terminal status for a controlled, bounded,
 * resumable background batch calculation job (TSK-M1-09).
 *
 * Epistemic & Operational Directives:
 * 1. Explicit Progress Accounting:
 *    Distinguishes total eligible cohort, total selected in batch, processed,
 *    successful, skipped (e.g. already calculated), failed, and remaining.
 * 2. Deterministic Status Progression:
 *    PENDING -> RUNNING -> COMPLETED / PARTIAL / FAILED.
 * 3. Non-Fabrication:
 *    Never reports a job as COMPLETED while eligible work within the requested batch remains unfinished.
 */
public record UniverseBatchJobStatusDto(
    @JsonProperty("jobId") String jobId,
    @JsonProperty("status") String status,
    @JsonProperty("startedAt") OffsetDateTime startedAt,
    @JsonProperty("completedAt") OffsetDateTime completedAt,
    @JsonProperty("operator") String operator,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("asOfDate") LocalDate asOfDate,
    @JsonProperty("requestedBatchSize") int requestedBatchSize,
    @JsonProperty("totalEligibleCohort") int totalEligibleCohort,
    @JsonProperty("totalSelected") int totalSelected,
    @JsonProperty("processedCount") int processedCount,
    @JsonProperty("successfulCount") int successfulCount,
    @JsonProperty("skippedCount") int skippedCount,
    @JsonProperty("failedCount") int failedCount,
    @JsonProperty("remainingCount") int remainingCount,
    @JsonProperty("currentOptionId") Long currentOptionId,
    @JsonProperty("currentOptionName") String currentOptionName,
    @JsonProperty("progressPercentage") double progressPercentage,
    @JsonProperty("latestReport") UniverseBatchRunReportDto latestReport,
    @JsonProperty("errorMessage") String errorMessage
) {
    public static UniverseBatchJobStatusDto idle() {
        return new UniverseBatchJobStatusDto(
            null, "IDLE", null, null, null, null, null,
            0, 0, 0, 0, 0, 0, 0, 0, null, null, 0.0, null, null
        );
    }
}
