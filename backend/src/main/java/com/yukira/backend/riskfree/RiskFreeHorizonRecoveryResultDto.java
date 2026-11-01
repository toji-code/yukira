package com.yukira.backend.riskfree;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Institutional audit result for bounded historical FBIL risk-free-rate recovery (Phase A5).
 *
 * Epistemic Rules:
 * - Transparently reports chunks attempted, recovered, failed, inserted, skipped, and revised.
 * - Leaves remaining gaps honestly exposed; never marks recovery complete if historical gaps persist.
 * - Preserves authentic cryptographic SHA-256 provenance for each recovered interval.
 */
public record RiskFreeHorizonRecoveryResultDto(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("benchmarkName") String benchmarkName,
    @JsonProperty("provider") String provider,
    @JsonProperty("daycountConvention") String daycountConvention,
    @JsonProperty("horizon") String horizon,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("windowStart") LocalDate windowStart,
    @JsonProperty("windowEnd") LocalDate windowEnd,
    @JsonProperty("initialGapsCount") int initialGapsCount,
    @JsonProperty("chunksAttempted") int chunksAttempted,
    @JsonProperty("chunksRecovered") int chunksRecovered,
    @JsonProperty("chunksFailed") int chunksFailed,
    @JsonProperty("rowsInserted") int rowsInserted,
    @JsonProperty("rowsSkipped") int rowsSkipped,
    @JsonProperty("rowsRevised") int rowsRevised,
    @JsonProperty("remainingGapsCount") int remainingGapsCount,
    @JsonProperty("recoveryStatus") String recoveryStatus, // FULLY_RECOVERED, PARTIALLY_RECOVERED, ALREADY_COVERED, FAILED, LIMIT_REACHED
    @JsonProperty("remainingGaps") List<RiskFreeGapInterval> remainingGaps,
    @JsonProperty("chunkAudits") List<IntervalRecoveryAudit> chunkAudits,
    @JsonProperty("message") String message,
    @JsonProperty("governanceDisclaimer") String governanceDisclaimer
) {
    public record IntervalRecoveryAudit(
        @JsonProperty("startDate") LocalDate startDate,
        @JsonProperty("endDate") LocalDate endDate,
        @JsonProperty("chunkIndex") int chunkIndex,
        @JsonProperty("sourceUri") String sourceUri,
        @JsonProperty("artifactReused") boolean artifactReused,
        @JsonProperty("sourceArtifactId") Long sourceArtifactId,
        @JsonProperty("rowsReceived") int rowsReceived,
        @JsonProperty("rowsInserted") int rowsInserted,
        @JsonProperty("duplicatesSkipped") int duplicatesSkipped,
        @JsonProperty("status") String status, // SUCCESS, PARTIAL, FAILED
        @JsonProperty("failureReason") String failureReason
    ) {}
}
