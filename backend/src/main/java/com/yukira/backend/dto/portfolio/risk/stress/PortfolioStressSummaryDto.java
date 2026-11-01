package com.yukira.backend.dto.portfolio.risk.stress;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * High-level consolidated summary across all evaluated historical stress episodes (Tasks 28, 37, 41, 45, 48, 49, 60, 69).
 *
 * Epistemic & Methodological Guardrails:
 * - Aggregation convention is explicitly defined: arithmetic mean across computable episodes only.
 * - Arithmetic episode mean is descriptive of the historical sample; does NOT represent expected future return,
 *   average annual return, or representative portfolio drawdown.
 * - Evidence accounting separates Total Evaluated, Eligible, Computable, Complete Window, Common-Date Subset,
 *   Withheld, Sufficient, Limited, Insufficient, Unavailable, and Fallback Benchmark episodes.
 * - Denominator is explicit: e.g. "COMPUTABLE_EPISODES_ONLY (n=X of Y eligible)".
 * - Worst drawdown and worst cumulative return are nullable (null when no computable episodes exist, rather than misleading 0.00% clamping) (Task 60).
 * - Explicit episode comparability grouping (Task 69): groups directly comparable, fallback benchmark, common-date subset,
 *   and withheld episodes with sub-group averages.
 * - Calculation input fingerprint is preserved for complete provenance (Task 61).
 */
public record PortfolioStressSummaryDto(
    int totalEpisodesEvaluated,
    int episodesEligible,
    int episodesComputable,
    int episodesCompleteWindow,
    int episodesCommonDateSubset,
    int episodesWithheld,
    int episodesSufficientEvidence,
    int episodesLimitedEvidence,
    int episodesInsufficientEvidence,
    int episodesUnavailable,
    int episodesExcluded,
    int episodesWithFallbackBenchmark,
    String aggregationDenominator,
    BigDecimal worstPortfolioDrawdownPct,
    String worstDrawdownEpisodeId,
    BigDecimal worstPortfolioCumulativeReturnPct,
    String worstReturnEpisodeId,
    BigDecimal averageStressCumulativeReturnPct,
    String mostResilientFundName,
    String greatestDetractorFundName,
    String overallStressAssessment,
    String aggregationConvention,
    String comparabilityNotice,
    List<String> comparabilityLimitations,
    String portfolioAllocationSnapshotId,
    String calculationRunId,
    int directlyComparableEpisodesCount,
    int fallbackBenchmarkEpisodesCount,
    int commonDateSubsetEpisodesCount,
    int withheldEpisodesCount,
    BigDecimal directlyComparableAverageReturnPct,
    BigDecimal fallbackBenchmarkAverageReturnPct,
    BigDecimal commonDateSubsetAverageReturnPct,
    String groupingExplanation,
    String calculationInputFingerprint
) {
    public int computableEpisodesCount() {
        return episodesComputable;
    }

    public String coverageLimitationDisclosure() {
        return groupingExplanation != null ? groupingExplanation
            : (comparabilityLimitations != null && !comparabilityLimitations.isEmpty() ? String.join("; ", comparabilityLimitations) : "NONE");
    }

    /**
     * Backward-compatible 26-parameter constructor (Tasks 48/49 compatibility).
     */
    public PortfolioStressSummaryDto(
        int totalEpisodesEvaluated,
        int episodesEligible,
        int episodesComputable,
        int episodesCompleteWindow,
        int episodesCommonDateSubset,
        int episodesWithheld,
        int episodesSufficientEvidence,
        int episodesLimitedEvidence,
        int episodesInsufficientEvidence,
        int episodesUnavailable,
        int episodesExcluded,
        int episodesWithFallbackBenchmark,
        String aggregationDenominator,
        BigDecimal worstPortfolioDrawdownPct,
        String worstDrawdownEpisodeId,
        BigDecimal worstPortfolioCumulativeReturnPct,
        String worstReturnEpisodeId,
        BigDecimal averageStressCumulativeReturnPct,
        String mostResilientFundName,
        String greatestDetractorFundName,
        String overallStressAssessment,
        String aggregationConvention,
        String comparabilityNotice,
        List<String> comparabilityLimitations,
        String portfolioAllocationSnapshotId,
        String calculationRunId
    ) {
        this(
            totalEpisodesEvaluated,
            episodesEligible,
            episodesComputable,
            episodesCompleteWindow,
            episodesCommonDateSubset,
            episodesWithheld,
            episodesSufficientEvidence,
            episodesLimitedEvidence,
            episodesInsufficientEvidence,
            episodesUnavailable,
            episodesExcluded,
            episodesWithFallbackBenchmark,
            aggregationDenominator,
            worstPortfolioDrawdownPct,
            worstDrawdownEpisodeId,
            worstPortfolioCumulativeReturnPct,
            worstReturnEpisodeId,
            averageStressCumulativeReturnPct,
            mostResilientFundName,
            greatestDetractorFundName,
            overallStressAssessment,
            aggregationConvention,
            comparabilityNotice,
            comparabilityLimitations,
            portfolioAllocationSnapshotId,
            calculationRunId,
            episodesCompleteWindow - episodesWithFallbackBenchmark,
            episodesWithFallbackBenchmark,
            episodesCommonDateSubset,
            episodesWithheld,
            null, // directlyComparableAverageReturnPct
            null, // fallbackBenchmarkAverageReturnPct
            null, // commonDateSubsetAverageReturnPct
            "Episodes grouped by benchmark comparability and observation window completeness.",
            "FP-" + (calculationRunId != null && calculationRunId.length() >= 8 ? calculationRunId.substring(0, 8) : "DEFAULT")
        );
    }

    /**
     * Backward-compatible 21-parameter constructor (Task 41 compatibility).
     */
    public PortfolioStressSummaryDto(
        int totalEpisodesEvaluated,
        int episodesEligible,
        int episodesComputable,
        int episodesSufficientEvidence,
        int episodesLimitedEvidence,
        int episodesInsufficientEvidence,
        int episodesUnavailable,
        int episodesExcluded,
        int episodesWithFallbackBenchmark,
        String aggregationDenominator,
        BigDecimal worstPortfolioDrawdownPct,
        String worstDrawdownEpisodeId,
        BigDecimal worstPortfolioCumulativeReturnPct,
        String worstReturnEpisodeId,
        BigDecimal averageStressCumulativeReturnPct,
        String mostResilientFundName,
        String greatestDetractorFundName,
        String overallStressAssessment,
        String aggregationConvention,
        String comparabilityNotice,
        List<String> comparabilityLimitations
    ) {
        this(
            totalEpisodesEvaluated,
            episodesEligible,
            episodesComputable,
            episodesComputable, // episodesCompleteWindow fallback
            0, // episodesCommonDateSubset fallback
            Math.max(0, episodesEligible - episodesComputable), // episodesWithheld fallback
            episodesSufficientEvidence,
            episodesLimitedEvidence,
            episodesInsufficientEvidence,
            episodesUnavailable,
            episodesExcluded,
            episodesWithFallbackBenchmark,
            aggregationDenominator,
            worstPortfolioDrawdownPct,
            worstDrawdownEpisodeId,
            worstPortfolioCumulativeReturnPct,
            worstReturnEpisodeId,
            averageStressCumulativeReturnPct,
            mostResilientFundName,
            greatestDetractorFundName,
            overallStressAssessment,
            aggregationConvention,
            comparabilityNotice,
            comparabilityLimitations,
            null, // portfolioAllocationSnapshotId default
            null  // calculationRunId default
        );
    }

    /**
     * Backward-compatible 19-parameter constructor.
     */
    public PortfolioStressSummaryDto(
        int totalEpisodesEvaluated,
        int episodesSufficientEvidence,
        int episodesLimitedEvidence,
        int episodesInsufficientEvidence,
        BigDecimal worstPortfolioDrawdownPct,
        String worstDrawdownEpisodeId,
        BigDecimal worstPortfolioCumulativeReturnPct,
        String worstReturnEpisodeId,
        BigDecimal averageStressCumulativeReturnPct,
        String mostResilientFundName,
        String greatestDetractorFundName,
        String overallStressAssessment,
        String aggregationConvention,
        String comparabilityNotice,
        List<String> comparabilityLimitations,
        int episodesUnavailable,
        int episodesExcluded,
        int episodesWithFallbackBenchmark,
        String aggregationDenominator
    ) {
        this(
            totalEpisodesEvaluated,
            episodesSufficientEvidence + episodesLimitedEvidence,
            episodesSufficientEvidence + episodesLimitedEvidence,
            episodesSufficientEvidence,
            episodesLimitedEvidence,
            0,
            episodesSufficientEvidence,
            episodesLimitedEvidence,
            episodesInsufficientEvidence,
            episodesUnavailable,
            episodesExcluded,
            episodesWithFallbackBenchmark,
            aggregationDenominator,
            worstPortfolioDrawdownPct,
            worstDrawdownEpisodeId,
            worstPortfolioCumulativeReturnPct,
            worstReturnEpisodeId,
            averageStressCumulativeReturnPct,
            mostResilientFundName,
            greatestDetractorFundName,
            overallStressAssessment,
            aggregationConvention,
            comparabilityNotice,
            comparabilityLimitations,
            null,
            null
        );
    }

    /**
     * Backward-compatible 16-parameter constructor (Task 28 compatibility).
     */
    public PortfolioStressSummaryDto(
        int totalEpisodesEvaluated,
        int episodesSufficientEvidence,
        int episodesLimitedEvidence,
        int episodesInsufficientEvidence,
        BigDecimal worstPortfolioDrawdownPct,
        String worstDrawdownEpisodeId,
        BigDecimal worstPortfolioCumulativeReturnPct,
        String worstReturnEpisodeId,
        BigDecimal averageStressCumulativeReturnPct,
        String mostResilientFundName,
        String greatestDetractorFundName,
        String overallStressAssessment,
        String aggregationConvention,
        String comparabilityNotice,
        List<String> comparabilityLimitations,
        int episodesUnavailable
    ) {
        this(
            totalEpisodesEvaluated,
            episodesSufficientEvidence,
            episodesLimitedEvidence,
            episodesInsufficientEvidence,
            worstPortfolioDrawdownPct,
            worstDrawdownEpisodeId,
            worstPortfolioCumulativeReturnPct,
            worstReturnEpisodeId,
            averageStressCumulativeReturnPct,
            mostResilientFundName,
            greatestDetractorFundName,
            overallStressAssessment,
            aggregationConvention,
            comparabilityNotice,
            comparabilityLimitations,
            episodesUnavailable,
            0,
            0,
            "ALL_EVALUATED_EPISODES"
        );
    }
}
