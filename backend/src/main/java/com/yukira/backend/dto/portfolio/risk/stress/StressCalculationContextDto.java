package com.yukira.backend.dto.portfolio.risk.stress;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable calculation run identity, content-addressed fingerprint, and reproducible execution context
 * for historical stress analysis (Tasks 49, 61, 64).
 *
 * Epistemic Guardrails:
 * - calculationRunId: Unique execution instance identifier for this invocation.
 * - calculationInputFingerprint: Deterministic, content-addressed SHA-256 fingerprint derived strictly
 *   from portfolio constituent weights, benchmark code, analysis cutoff, knowledge cutoff, and methodology versions (Task 61).
 * - Tracks benchmark and constituent source data revision references (Task 64).
 * - Distinguishes authoritative source tracking from uncorroborated or missing lineage.
 */
public record StressCalculationContextDto(
    String calculationRunId,
    String calculationInputFingerprint,
    String portfolioAllocationSnapshotId,
    String benchmarkCode,
    LocalDate analysisCutoffDate,
    OffsetDateTime knowledgeCutoff,
    String methodologyVersion,
    String identificationPolicyVersion,
    String evidencePolicyVersion,
    String constructionConvention,
    String attributionMethodology,
    boolean isReproducibleWithRecordedCutoff,
    String reproducibilityNotes,
    String benchmarkSourceRevisionId,
    Map<String, String> constituentSourceRevisions,
    String sourceLineageState,
    String sourceLineageLimitation
) {
    public String benchmarkSourceRevision() {
        return benchmarkSourceRevisionId;
    }

    /**
     * Backward-compatible 14-parameter constructor (PortfolioHistoricalStressService call compatibility).
     */
    public StressCalculationContextDto(
        String calculationRunId,
        String portfolioAllocationSnapshotId,
        String benchmarkCode,
        String identificationPolicyVersion,
        String evidencePolicyVersion,
        String methodologyVersion,
        String attributionMethodology,
        String constructionConvention,
        LocalDate analysisCutoffDate,
        OffsetDateTime knowledgeCutoff,
        OffsetDateTime executedAt,
        double attributionTolerance,
        boolean isReproducibleWithRecordedCutoff,
        String reproducibilityNotes
    ) {
        this(
            calculationRunId,
            "FP-" + (calculationRunId != null && calculationRunId.length() >= 8 ? calculationRunId.substring(0, 8) : "DEFAULT"),
            portfolioAllocationSnapshotId,
            benchmarkCode,
            analysisCutoffDate,
            knowledgeCutoff,
            methodologyVersion,
            identificationPolicyVersion,
            evidencePolicyVersion,
            constructionConvention,
            attributionMethodology,
            isReproducibleWithRecordedCutoff,
            reproducibilityNotes,
            null, // benchmarkSourceRevisionId
            Collections.emptyMap(), // constituentSourceRevisions
            "PARTIAL_SOURCE_LINEAGE_AVAILABLE",
            "Source artifact revision tracking recorded where cryptographically available; observations grounded in point-in-time database records."
        );
    }

    /**
     * Backward-compatible 12-parameter constructor (Task 49 compatibility).
     */
    public StressCalculationContextDto(
        String calculationRunId,
        String portfolioAllocationSnapshotId,
        String benchmarkCode,
        LocalDate analysisCutoffDate,
        OffsetDateTime knowledgeCutoff,
        String methodologyVersion,
        String identificationPolicyVersion,
        String evidencePolicyVersion,
        String constructionConvention,
        String attributionMethodology,
        boolean isReproducibleWithRecordedCutoff,
        String reproducibilityNotes
    ) {
        this(
            calculationRunId,
            "FP-" + (calculationRunId != null && calculationRunId.length() >= 8 ? calculationRunId.substring(0, 8) : "DEFAULT"),
            portfolioAllocationSnapshotId,
            benchmarkCode,
            analysisCutoffDate,
            knowledgeCutoff,
            methodologyVersion,
            identificationPolicyVersion,
            evidencePolicyVersion,
            constructionConvention,
            attributionMethodology,
            isReproducibleWithRecordedCutoff,
            reproducibilityNotes,
            null, // benchmarkSourceRevisionId
            Collections.emptyMap(), // constituentSourceRevisions
            "PARTIAL_SOURCE_LINEAGE_AVAILABLE",
            "Source artifact revision tracking recorded where cryptographically available; observations grounded in point-in-time database records."
        );
    }
}
