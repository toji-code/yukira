package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Audit result of bounded historical recovery for a benchmark horizon (Phase A4).
 */
public record BenchmarkHorizonRecoveryResultDto(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("benchmarkName") String benchmarkName,
    @JsonProperty("provider") String provider,
    @JsonProperty("returnVariant") String returnVariant,
    @JsonProperty("horizon") String horizon,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("windowStart") LocalDate windowStart,
    @JsonProperty("windowEnd") LocalDate windowEnd,
    @JsonProperty("totalGapsIdentified") int totalGapsIdentified,
    @JsonProperty("intervalsAttempted") int intervalsAttempted,
    @JsonProperty("intervalsRecovered") int intervalsRecovered,
    @JsonProperty("intervalsFailed") int intervalsFailed,
    @JsonProperty("observationsInserted") int observationsInserted,
    @JsonProperty("duplicatesSkipped") int duplicatesSkipped,
    @JsonProperty("observationsRevised") int observationsRevised,
    @JsonProperty("remainingGapsCount") int remainingGapsCount,
    @JsonProperty("status") String status, // FULLY_RECOVERED, PARTIALLY_RECOVERED, ALREADY_COVERED, FAILED
    @JsonProperty("failureReason") String failureReason,
    @JsonProperty("intervalAudits") List<IntervalRecoveryAudit> intervalAudits,
    @JsonProperty("governanceNote") String governanceNote
) {
    public record IntervalRecoveryAudit(
        @JsonProperty("intervalStart") LocalDate intervalStart,
        @JsonProperty("intervalEnd") LocalDate intervalEnd,
        @JsonProperty("chunkIndex") int chunkIndex,
        @JsonProperty("sourceUri") String sourceUri,
        @JsonProperty("reusedStoredArtifact") boolean reusedStoredArtifact,
        @JsonProperty("sourceArtifactId") Long sourceArtifactId,
        @JsonProperty("rowsReceived") int rowsReceived,
        @JsonProperty("rowsInserted") int rowsInserted,
        @JsonProperty("duplicatesSkipped") int duplicatesSkipped,
        @JsonProperty("status") String status, // SUCCESS, PARTIAL, SKIPPED, FAILED
        @JsonProperty("error") String error
    ) {}
}
