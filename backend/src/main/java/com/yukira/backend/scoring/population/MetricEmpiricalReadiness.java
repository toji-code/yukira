package com.yukira.backend.scoring.population;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Per-metric empirical calibration readiness with full, non-collapsed epistemic states.
 *
 * Each state is reported independently and must never be inferred from another:
 *  NOT_LOADED         - fewer than two peers carry any NAV observation at all.
 *  LOADED             - peer NAV series are present in the database.
 *  ELIGIBLE           - at least {@code MIN_OBS_THRESHOLD} peers carry >= 700 PIT observations.
 *  CALCULATED         - the canonical quant engine persisted a CALCULATED MetricResult for at
 *                       least one peer under the exact calibration configuration.
 *  EMPIRICALLY_READY  - at least {@code MIN_COHORT_FOR_SIGNIFICANCE} peers hold a CALCULATED
 *                       canonical MetricResult, so percentiles are statistically meaningful.
 *  VALIDATED          - requires multi-cycle empirical study plus governance authorization. Never
 *                       asserted by this service.
 *  APPROVED           - requires formal project review committee authorization. Never asserted
 *                       by this service.
 */
public record MetricEmpiricalReadiness(
    String metricCode,
    String metricName,
    String units,
    String peerLoadState,
    String peerEligibilityState,
    String canonicalCalculationState,
    String empiricalReadinessState,
    boolean empiricallyReady,
    boolean validated,
    boolean approved,

    int cohortCandidateCount,
    int peersWithAnyNavCount,
    int eligiblePeerCount,
    int excludedByNavCoverageCount,
    int calculatedPeerCount,
    int missingPeerCount,
    int excludedPeerCount,
    int minimumPeersRequiredForSignificance,

    LocalDate analysisStartDate,
    LocalDate analysisEndDate,
    OffsetDateTime knowledgeCutoffTime,
    String benchmarkCode,
    String methodologyTag,

    EmpiricalDistributionSummary empiricalDistribution,
    ProvisionalVsEmpiricalDiff provisionalVsEmpirical,
    List<String> notes
) {}
