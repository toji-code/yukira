package com.yukira.backend.dto.portfolio.risk;

/**
 * Deterministic diagnostic finding generated from portfolio risk, lookback sensitivity,
 * or historical stress analysis (Tasks 44, 50, 65, 71).
 *
 * Epistemic & Version Continuity Guardrails:
 * - Immutable references to original episode, calculation run, allocation snapshot, and calculation fingerprint (Task 65).
 * - Stable findingId tracks the logical finding across recurrent runs while instance id is unique to this execution.
 * - Explicit lifecycleStatus tracks ACTIVE vs SUPERSEDED vs REPRODUCED_UNCHANGED status across recalculations.
 * - Every finding is explicitly grounded in an empirical investigationQuestion rather than speculative predictions (Task 71).
 */
public record RiskInvestigationFindingDto(
    String id,
    String findingType, // OBSERVED_FACT, CALCULATED_RISK, INTERPRETATION, DATA_LIMITATION, UNKNOWN
    String title,
    String headline,
    String evidence,
    String suggestedInvestigation,
    String actionableWorkspacePrompt,
    String calculationRunId,
    String episodeId,
    String allocationSnapshotId,
    String findingVersion,
    String evidenceState,
    boolean isSuperseded,
    String supersededByRunId,
    String findingId,
    String lifecycleStatus, // ACTIVE, SUPERSEDED, ARCHIVED, REPRODUCED_UNCHANGED
    String calculationInputFingerprint,
    String supersededByFindingId,
    String investigationQuestion
) {
    /**
     * Backward-compatible 14-parameter constructor (Task 50 compatibility).
     */
    public RiskInvestigationFindingDto(
        String id,
        String findingType,
        String title,
        String headline,
        String evidence,
        String suggestedInvestigation,
        String actionableWorkspacePrompt,
        String calculationRunId,
        String episodeId,
        String allocationSnapshotId,
        String findingVersion,
        String evidenceState,
        boolean isSuperseded,
        String supersededByRunId
    ) {
        this(
            id,
            findingType,
            title,
            headline,
            evidence,
            suggestedInvestigation,
            actionableWorkspacePrompt,
            calculationRunId,
            episodeId,
            allocationSnapshotId,
            findingVersion,
            evidenceState,
            isSuperseded,
            supersededByRunId,
            id, // findingId default
            isSuperseded ? "SUPERSEDED" : "ACTIVE",
            null, // calculationInputFingerprint
            null, // supersededByFindingId
            suggestedInvestigation // investigationQuestion default
        );
    }

    /**
     * Backward-compatible 7-parameter constructor for pre-Task 50 call sites.
     */
    public RiskInvestigationFindingDto(
        String id,
        String findingType,
        String title,
        String headline,
        String evidence,
        String suggestedInvestigation,
        String actionableWorkspacePrompt
    ) {
        this(
            id,
            findingType,
            title,
            headline,
            evidence,
            suggestedInvestigation,
            actionableWorkspacePrompt,
            null,
            null,
            null,
            "V1",
            "UNSPECIFIED",
            false,
            null
        );
    }
}
