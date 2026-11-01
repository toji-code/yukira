package com.yukira.backend.dto.portfolio.risk.stress;

import com.yukira.backend.dto.portfolio.risk.UnvaluedPositionDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Authoritative response payload for Historical Portfolio Stress-Period Analysis (Tasks 25–44, 45, 48, 49, 61, 64, 74).
 *
 * Epistemic & Methodological Guardrails:
 * 1. Model: Static Current-Allocation Constant-Weight Daily Rebalanced Historical Stress Proxy
 *    (R_{p,t} = \sum w_i R_{i,t}). Mathematical proxy; does NOT reconstruct realized investor historical returns.
 * 2. Multi-Period Attribution: Carino Logarithmic Linking ensures exact additive decomposition
 *    where constituent contributions sum to portfolio cumulative return without compounding gaps.
 * 3. Point-in-Time & Provenance: effective_date <= asOfDate and availability_time <= knowledgeCutoff.
 *    Full execution provenance includes calculationRunId, calculationInputFingerprint, allocationSnapshot,
 *    calculationContext, sourceLineageState, and evaluationContextType (Task 61, 64, 74).
 * 4. Governance: Candidate methodology (PORTFOLIO_STRESS_METHODOLOGY_V2,
 *    PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2 & PORTFOLIO_STRESS_EVIDENCE_POLICY_V2).
 * 5. Honest Gating: Episodes lacking constituent history are flagged as LIMITED_EVIDENCE or
 *    INSUFFICIENT_EVIDENCE rather than fabricated or filled with synthetic NAVs.
 */
public record PortfolioStressAnalysisResponseDto(
    String evaluationStatus,
    StressBenchmarkReferenceDto benchmarkReference,
    PortfolioStressSummaryDto summary,
    List<PortfolioStressEpisodeDto> episodes,
    String evidencePolicyVersion,
    String identificationPolicyVersion,
    String methodologyVersion,
    String portfolioConstructionConvention,
    String attributionMethodology,
    List<String> governanceDisclaimers,
    Map<String, String> methodologyNotes,
    BigDecimal knownValueCoveragePct,
    List<UnvaluedPositionDto> unvaluedPositions,
    String calculationRunId,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    BigDecimal reconciliationTolerance,
    String overallComparabilityState,
    PortfolioAllocationSnapshotDto allocationSnapshot,
    StressCalculationContextDto calculationContext,
    String calculationInputFingerprint,
    String sourceLineageState,
    String evaluationContextType
) {
    /**
     * Backward-compatible 20-parameter constructor (Task 48/49 compatibility).
     */
    public PortfolioStressAnalysisResponseDto(
        String evaluationStatus,
        StressBenchmarkReferenceDto benchmarkReference,
        PortfolioStressSummaryDto summary,
        List<PortfolioStressEpisodeDto> episodes,
        String evidencePolicyVersion,
        String identificationPolicyVersion,
        String methodologyVersion,
        String portfolioConstructionConvention,
        String attributionMethodology,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        String calculationRunId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal reconciliationTolerance,
        String overallComparabilityState,
        PortfolioAllocationSnapshotDto allocationSnapshot,
        StressCalculationContextDto calculationContext
    ) {
        this(
            evaluationStatus,
            benchmarkReference,
            summary,
            episodes,
            evidencePolicyVersion,
            identificationPolicyVersion,
            methodologyVersion,
            portfolioConstructionConvention,
            attributionMethodology,
            governanceDisclaimers,
            methodologyNotes,
            knownValueCoveragePct,
            unvaluedPositions,
            calculationRunId,
            asOfDate,
            knowledgeCutoff,
            reconciliationTolerance,
            overallComparabilityState,
            allocationSnapshot,
            calculationContext,
            calculationContext != null && calculationContext.calculationInputFingerprint() != null
                ? calculationContext.calculationInputFingerprint()
                : ("FP-" + (calculationRunId != null && calculationRunId.length() >= 8 ? calculationRunId.substring(0, 8) : "DEFAULT")),
            calculationContext != null && calculationContext.sourceLineageState() != null
                ? calculationContext.sourceLineageState()
                : "PARTIAL_SOURCE_LINEAGE_AVAILABLE",
            asOfDate != null && asOfDate.isBefore(LocalDate.now().minusDays(7))
                ? "HISTORICAL_AUDIT_CONTEXT"
                : "LATEST_POINT_IN_TIME"
        );
    }

    /**
     * Backward-compatible 18-parameter constructor (Task 42 compatibility).
     */
    public PortfolioStressAnalysisResponseDto(
        String evaluationStatus,
        StressBenchmarkReferenceDto benchmarkReference,
        PortfolioStressSummaryDto summary,
        List<PortfolioStressEpisodeDto> episodes,
        String evidencePolicyVersion,
        String identificationPolicyVersion,
        String methodologyVersion,
        String portfolioConstructionConvention,
        String attributionMethodology,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        String calculationRunId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal reconciliationTolerance,
        String overallComparabilityState
    ) {
        this(
            evaluationStatus,
            benchmarkReference,
            summary,
            episodes,
            evidencePolicyVersion,
            identificationPolicyVersion,
            methodologyVersion,
            portfolioConstructionConvention,
            attributionMethodology,
            governanceDisclaimers,
            methodologyNotes,
            knownValueCoveragePct,
            unvaluedPositions,
            calculationRunId,
            asOfDate,
            knowledgeCutoff,
            reconciliationTolerance,
            overallComparabilityState,
            null,
            null
        );
    }

    /**
     * Backward-compatible 13-parameter constructor (Task 24 compatibility).
     */
    public PortfolioStressAnalysisResponseDto(
        String evaluationStatus,
        StressBenchmarkReferenceDto benchmarkReference,
        PortfolioStressSummaryDto summary,
        List<PortfolioStressEpisodeDto> episodes,
        String evidencePolicyVersion,
        String identificationPolicyVersion,
        String methodologyVersion,
        String portfolioConstructionConvention,
        String attributionMethodology,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions
    ) {
        this(
            evaluationStatus,
            benchmarkReference,
            summary,
            episodes,
            evidencePolicyVersion,
            identificationPolicyVersion,
            methodologyVersion,
            portfolioConstructionConvention,
            attributionMethodology,
            governanceDisclaimers,
            methodologyNotes,
            knownValueCoveragePct,
            unvaluedPositions,
            null, // calculationRunId default
            null, // asOfDate default
            null, // knowledgeCutoff default
            BigDecimal.valueOf(0.0001), // reconciliationTolerance default (0.0001%)
            "DIRECTLY_COMPARABLE" // overallComparabilityState default
        );
    }

    /**
     * Backward-compatible 10-parameter constructor (Task 23 compatibility).
     */
    public PortfolioStressAnalysisResponseDto(
        String evaluationStatus,
        StressBenchmarkReferenceDto benchmarkReference,
        PortfolioStressSummaryDto summary,
        List<PortfolioStressEpisodeDto> episodes,
        String evidencePolicyVersion,
        String identificationPolicyVersion,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions
    ) {
        this(
            evaluationStatus,
            benchmarkReference,
            summary,
            episodes,
            evidencePolicyVersion,
            identificationPolicyVersion,
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "CONSTANT_WEIGHT_DAILY_REBALANCED_PROXY",
            "CARINO_DAILY_REBALANCED_LOGARITHMIC_LINKING",
            governanceDisclaimers,
            methodologyNotes,
            knownValueCoveragePct,
            unvaluedPositions
        );
    }
}
