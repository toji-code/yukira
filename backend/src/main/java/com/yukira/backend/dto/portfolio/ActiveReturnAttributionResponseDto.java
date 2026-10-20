package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative response DTO for Point-in-Time Fund Style Drift, Active Return Attribution
 * & Evidence-Gated Brinson Analysis.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Phase 2H/2K):
 * 1. Definitive Brinson sector allocation & security selection effects are strictly gated
 *    when benchmark constituent weights are partially disclosed (< 99.50% coverage).
 * 2. Point-in-Time look-ahead bias elimination: effectiveDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 * 3. Exact Mathematical Reconciliation:
 *    Macro: R_A = R_market_tilt + R_cash_drag + R_selection_sector_residual (within 1e-6).
 *    Brinson: R_A = Sum(Allocation) + Sum(Selection) + Sum(Interaction) + CoverageResidual.
 * 4. Non-Advisory: Statistical associations describe portfolio observations, never manager skill or alpha proof.
 */
public record ActiveReturnAttributionResponseDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String schemeCode,
    String benchmarkCode,
    String benchmarkName,
    String asOfDate,
    String knowledgeCutoff,
    String attributionPeriodStart,
    String attributionPeriodEnd,
    int observationCount,

    // Core Returns
    BigDecimal fundReturnPct,
    String fundReturnPctFormatted,
    BigDecimal benchmarkReturnPct,
    String benchmarkReturnPctFormatted,
    BigDecimal activeReturnPct,
    String activeReturnPctFormatted,

    // Model Status & Methodology
    String modelStatus,
    String modelName,
    String modelExplanation,

    // Macro & Systematic Active Decomposition
    MacroActiveDecompositionDto macroAttribution,

    // Style Allocation Context (from PIT AMFI classification)
    ActiveStyleContextDto styleContext,

    // Exposure Drift Timeline across historical snapshots
    List<ExposureTimelineObservationDto> exposureTimeline,

    // Brinson Segmentation Status & Effects
    BrinsonAttributionSummaryDto brinsonSegmentation,

    // Data-Readiness Gate Matrix
    AttributionDataReadinessGateDto dataReadinessGate,

    // Investor Due-Diligence Investigation Prompts
    List<String> investigationPrompts,

    // Epistemic Limitations
    List<String> epistemicLimitations,

    // Cryptographic & Provenance Audit Trail
    AttributionProvenanceDto provenance
) {
    public record MacroActiveDecompositionDto(
        BigDecimal empiricalBeta,
        BigDecimal marketSensitivityContributionPct,
        String marketSensitivityContributionPctFormatted,
        BigDecimal initialCashWeightPct,
        String initialCashWeightPctFormatted,
        BigDecimal riskFreeReturnPct,
        String riskFreeReturnPctFormatted,
        BigDecimal cashDragContributionPct,
        String cashDragContributionPctFormatted,
        BigDecimal selectionAndSectorResidualPct,
        String selectionAndSectorResidualPctFormatted,
        BigDecimal reconciledActiveReturnPct,
        String reconciledActiveReturnPctFormatted,
        BigDecimal reconciliationToleranceDeltaPct,
        boolean isReconciled,
        String reconciliationFormula
    ) {}

    public record ActiveStyleContextDto(
        BigDecimal largeCapWeightPct,
        String largeCapWeightPctFormatted,
        BigDecimal midCapWeightPct,
        String midCapWeightPctFormatted,
        BigDecimal smallCapWeightPct,
        String smallCapWeightPctFormatted,
        BigDecimal cashWeightPct,
        String cashWeightPctFormatted,
        BigDecimal unclassifiedWeightPct,
        String unclassifiedWeightPctFormatted,
        String classificationStatus,
        String classificationRankingPeriod,
        String activeTiltSummary
    ) {}

    public record ExposureTimelineObservationDto(
        Long snapshotId,
        LocalDate portfolioDate,
        OffsetDateTime availabilityTime,
        String disclosureScope, // "FULL_PORTFOLIO_DISCLOSURE" or "PARTIAL_TOP_HOLDINGS"
        BigDecimal reportedWeightSumPct,
        int holdingsCount,
        BigDecimal classificationCoveragePct,
        BigDecimal largeCapWeightPct,
        BigDecimal midCapWeightPct,
        BigDecimal smallCapWeightPct,
        BigDecimal cashWeightPct,
        BigDecimal unclassifiedWeightPct,
        List<SectorWeightSummaryDto> topSectors,
        String comparabilityStatus, // "BASELINE_OBSERVATION", "COMPARABLE_FULL_DISCLOSURE", "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT", "NON_COMPARABLE_TEMPORAL_GAP"
        String comparabilityReason,
        Long daysSincePreviousSnapshot,
        String sourceStorageUri,
        String sourceSha256
    ) {}

    public record SectorWeightSummaryDto(
        String sectorName,
        BigDecimal weightPct,
        int holdingCount
    ) {}

    public record BrinsonAttributionSummaryDto(
        String executionStatus,
        String brinsonVariant,
        String statusExplanation,
        String reasonIfNotCalculated,
        BigDecimal totalAllocationEffectPct,
        BigDecimal totalSelectionEffectPct,
        BigDecimal totalInteractionEffectPct,
        BigDecimal totalActiveReturnReconciledPct,
        BigDecimal reconciliationDeltaPct,
        boolean isReconciled,
        BigDecimal benchmarkDisclosedWeightPct,
        int benchmarkWeightedCount,
        int benchmarkTotalCount,
        List<String> missingPrerequisites,
        List<BrinsonSegmentDto> segments
    ) {
        // Backward-compatible constructor for 7-parameter callers
        public BrinsonAttributionSummaryDto(
            String executionStatus,
            String brinsonVariant,
            String reasonIfNotCalculated,
            BigDecimal totalAllocationEffectPct,
            BigDecimal totalSelectionEffectPct,
            BigDecimal totalInteractionEffectPct,
            List<BrinsonSegmentDto> segments
        ) {
            this(
                executionStatus,
                brinsonVariant,
                reasonIfNotCalculated,
                reasonIfNotCalculated,
                totalAllocationEffectPct,
                totalSelectionEffectPct,
                totalInteractionEffectPct,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                0,
                0,
                Collections.emptyList(),
                segments
            );
        }
    }

    public record BrinsonSegmentDto(
        String segmentName,
        BigDecimal portfolioWeightPct,
        BigDecimal benchmarkWeightPct,
        BigDecimal portfolioReturnPct,
        BigDecimal benchmarkReturnPct,
        BigDecimal allocationEffectPct,
        BigDecimal selectionEffectPct,
        BigDecimal interactionEffectPct,
        BigDecimal totalSegmentEffectPct
    ) {}

    public record AttributionDataReadinessGateDto(
        String overallReadiness,
        int totalDependencies,
        int availableDependencies,
        int missingDependencies,
        List<DataDependencyItemDto> dependencies
    ) {}

    public record DataDependencyItemDto(
        String dependencyCode,
        String dependencyName,
        String requiredFor,
        String repositoryStatus,
        String officialSource,
        String epistemicNote
    ) {}

    public record AttributionProvenanceDto(
        String methodologyVersion,
        String calculationRunTimestamp,
        String formulaSpecification,
        String fundNavSourceSha256,
        String benchmarkObservationSha256,
        String riskFreeSourceDescription,
        String nonAdvisoryDisclaimer
    ) {}
}
