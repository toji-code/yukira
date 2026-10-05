package com.yukira.backend.scoring.population;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable audit report evaluating whether the database possesses sufficient
 * empirical mutual fund universe evidence to construct empirical peer distributions.
 *
 * Epistemic rule:
 * LOADED != ELIGIBLE != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED.
 * This report is evidence only: the active provisional reference population is never replaced.
 */
public record PeerPopulationEvaluationReport(
    String targetCategory,
    String activeProvisionalPopulation,
    String status,
    long catalogUniverseCandidatesCount,
    long optionsWithSufficientNavCount,
    long optionsWithPartialNavCount,
    long optionsWithInsufficientNavCount,
    long optionsFailedCount,
    int minimumFundsRequiredForSignificance,
    boolean empiricallyReady,
    List<PeerFundCoverageSummary> peerCoverages,
    Map<String, EmpiricalDistributionSummary> candidateEmpiricalDistributions,
    List<MetricEmpiricalReadiness> metricReadiness,
    BenchmarkCoverageInfo benchmarkCoverage,
    List<String> readinessLadder,
    List<String> canonicalScoreMetricCodes,
    LocalDate analysisStartDate,
    LocalDate analysisEndDate,
    OffsetDateTime knowledgeCutoffTime,
    int peersWithAnyNavCount,
    int calibrationEligiblePeerCount,
    int eligiblePeersExcludingPilotSubject,
    List<String> dataGaps,
    String governanceNote
) {
    /**
     * Backwards-compatible canonical constructor for existing callers and tests.
     * Leaves every new evidence field empty; NEVER invents an empirical distribution.
     */
    public PeerPopulationEvaluationReport(
        String targetCategory,
        String activeProvisionalPopulation,
        String status,
        long catalogUniverseCandidatesCount,
        long optionsWithSufficientNavCount,
        int minimumFundsRequiredForSignificance,
        boolean empiricalCalculationReady,
        List<String> dataGaps,
        String governanceNote
    ) {
        this(
            targetCategory,
            activeProvisionalPopulation,
            status,
            catalogUniverseCandidatesCount,
            optionsWithSufficientNavCount,
            0L,
            Math.max(0, catalogUniverseCandidatesCount - optionsWithSufficientNavCount),
            0L,
            minimumFundsRequiredForSignificance,
            false,
            List.of(),
            Map.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            0,
            0,
            (int) Math.max(0, optionsWithSufficientNavCount),
            dataGaps,
            governanceNote
        );
    }
}
