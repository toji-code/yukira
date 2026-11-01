package com.yukira.backend.dto.portfolio.risk.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Constituent mutual fund return contribution, drawdown behavior, and observation coverage
 * during a historical stress episode (Tasks 25, 27, 35, 40, 46, 52, 53, 54, 60, 68, 70, 75).
 *
 * Epistemic & Methodological Guardrails:
 * - Under the Static Current-Allocation Constant-Weight Daily Rebalanced Proxy model:
 *     1. fundCumulativeReturnPct: R_{i,cum} = \prod (1 + R_{i,t}) - 1
 *     2. approximateWeightedReturnIndicatorPct: w_i * R_{i,cum} (single-period weighted return proxy;
 *        does NOT sum to portfolio cumulative return across multi-period compounding).
 *     3. exactAdditiveReturnContributionPct: A_i derived via Carino multi-period logarithmic linking,
 *        guaranteeing \sum A_i = R_{p,cum} exactly within 0.0001% tolerance.
 *     4. weightedReturnToLinkedAttributionDifferencePct: (w_i * R_{i,cum} - A_i), measuring the
 *        compounding and daily rebalancing path-dependency difference.
 *     5. rebalancingInteractionPct: maintained as backward-compatible alias.
 *     6. Date-Level Observation Accounting: expectedObservations, availableObservations,
 *        usableObservationCount, excludedObservationCount, preInceptionDateCount, missingNavDateCount,
 *        invalidNavDateCount, constituentCoveragePct, hasPreInception, earliestKnownNavDate.
 *     7. Attribution Role Governance (Tasks 40, 46, 70):
 *        Separates standalonePerformanceRole from linkedAttributionRole.
 *        If Carino attribution residual exceeds 0.0001% pts, linked role is RECONCILIATION_DISCREPANCY_WITHHELD.
 *     8. PRIMARY_DETRACTOR is defined strictly by largest negative linked contribution A_i,
 *        and is never conflated with worst standalone return.
 *     9. Numerical validity state (Task 53 & 60) and constituent measurement window coverage (Task 52).
 *     10. Explicit withholding reason tracking (Task 75) and constituent source quality state (Task 68).
 */
public record FundStressContributionDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String category,
    BigDecimal currentAllocationWeightPct,
    BigDecimal fundCumulativeReturnPct,
    BigDecimal weightedReturnContributionPct,
    BigDecimal approximateWeightedReturnIndicatorPct,
    BigDecimal exactAdditiveReturnContributionPct,
    BigDecimal weightedReturnToLinkedAttributionDifferencePct,
    BigDecimal rebalancingInteractionPct,
    BigDecimal fundMaxDrawdownPct,
    LocalDate fundPeakDate,
    LocalDate fundTroughDate,
    Integer alignedObservationCount,
    Integer expectedObservations,
    Integer availableObservations,
    BigDecimal constituentCoveragePct,
    boolean hasPreInception,
    LocalDate earliestKnownNavDate,
    String evidenceState,
    String metricEvidenceState,
    String attributionRole,
    String notes,
    String attributionSign,
    boolean outperformedPortfolioProxy,
    String attributionRoleInterpretation,
    Integer usableObservationCount,
    Integer excludedObservationCount,
    Integer preInceptionDateCount,
    Integer missingNavDateCount,
    Integer invalidNavDateCount,
    String standalonePerformanceRole,
    String linkedAttributionRole,
    String numericalValidityState,
    String constituentWindowCoverage,
    Integer tradingDaysInWindow,
    Integer calendarDaysInWindow,
    String sourceQualityState,
    String attributionReconciliationStatus,
    String withholdingReasonCode,
    String withholdingReasonExplanation
) {
    /**
     * Backward-compatible 36-parameter constructor (Task 52/54 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal approximateWeightedReturnIndicatorPct,
        BigDecimal exactAdditiveReturnContributionPct,
        BigDecimal weightedReturnToLinkedAttributionDifferencePct,
        BigDecimal rebalancingInteractionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        Integer expectedObservations,
        Integer availableObservations,
        BigDecimal constituentCoveragePct,
        boolean hasPreInception,
        LocalDate earliestKnownNavDate,
        String evidenceState,
        String metricEvidenceState,
        String attributionRole,
        String notes,
        String attributionSign,
        boolean outperformedPortfolioProxy,
        String attributionRoleInterpretation,
        Integer usableObservationCount,
        Integer excludedObservationCount,
        Integer preInceptionDateCount,
        Integer missingNavDateCount,
        Integer invalidNavDateCount,
        String standalonePerformanceRole,
        String linkedAttributionRole,
        String numericalValidityState,
        String constituentWindowCoverage,
        Integer tradingDaysInWindow,
        Integer calendarDaysInWindow
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            approximateWeightedReturnIndicatorPct,
            exactAdditiveReturnContributionPct,
            weightedReturnToLinkedAttributionDifferencePct,
            rebalancingInteractionPct,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            expectedObservations,
            availableObservations,
            constituentCoveragePct,
            hasPreInception,
            earliestKnownNavDate,
            evidenceState,
            metricEvidenceState,
            attributionRole,
            notes,
            attributionSign,
            outperformedPortfolioProxy,
            attributionRoleInterpretation,
            usableObservationCount,
            excludedObservationCount,
            preInceptionDateCount,
            missingNavDateCount,
            invalidNavDateCount,
            standalonePerformanceRole,
            linkedAttributionRole,
            numericalValidityState,
            constituentWindowCoverage,
            tradingDaysInWindow,
            calendarDaysInWindow,
            "VALID", // sourceQualityState default
            exactAdditiveReturnContributionPct != null ? "RECONCILED_WITHIN_TOLERANCE" : "ATTRIBUTION_WITHHELD",
            exactAdditiveReturnContributionPct != null ? "ELIGIBLE_CALCULATED" : "WITHHELD_INSUFFICIENT_OBSERVATIONS",
            exactAdditiveReturnContributionPct != null ? "Calculated via Carino multi-period logarithmic linking." : "Attribution withheld."
        );
    }

    /**
     * Backward-compatible 32-parameter constructor (Task 40/44 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal approximateWeightedReturnIndicatorPct,
        BigDecimal exactAdditiveReturnContributionPct,
        BigDecimal weightedReturnToLinkedAttributionDifferencePct,
        BigDecimal rebalancingInteractionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        Integer expectedObservations,
        Integer availableObservations,
        BigDecimal constituentCoveragePct,
        boolean hasPreInception,
        LocalDate earliestKnownNavDate,
        String evidenceState,
        String metricEvidenceState,
        String attributionRole,
        String notes,
        String attributionSign,
        boolean outperformedPortfolioProxy,
        String attributionRoleInterpretation,
        Integer usableObservationCount,
        Integer excludedObservationCount,
        Integer preInceptionDateCount,
        Integer missingNavDateCount,
        Integer invalidNavDateCount
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            approximateWeightedReturnIndicatorPct,
            exactAdditiveReturnContributionPct,
            weightedReturnToLinkedAttributionDifferencePct,
            rebalancingInteractionPct,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            expectedObservations,
            availableObservations,
            constituentCoveragePct,
            hasPreInception,
            earliestKnownNavDate,
            evidenceState,
            metricEvidenceState,
            attributionRole,
            notes,
            attributionSign,
            outperformedPortfolioProxy,
            attributionRoleInterpretation,
            usableObservationCount,
            excludedObservationCount,
            preInceptionDateCount,
            missingNavDateCount,
            invalidNavDateCount,
            determineStandaloneRole(fundCumulativeReturnPct, outperformedPortfolioProxy, hasPreInception),
            attributionRole != null ? attributionRole : "NEUTRAL_CONTRIBUTOR",
            "NUMERICALLY_VALID",
            hasPreInception ? "INCOMPLETE_CONSTITUENT_WINDOW" : "FULL_BENCHMARK_EPISODE",
            usableObservationCount != null ? usableObservationCount : alignedObservationCount,
            null
        );
    }

    /**
     * Backward-compatible 24-parameter constructor (Task 28-34 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal approximateWeightedReturnIndicatorPct,
        BigDecimal exactAdditiveReturnContributionPct,
        BigDecimal weightedReturnToLinkedAttributionDifferencePct,
        BigDecimal rebalancingInteractionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        Integer expectedObservations,
        Integer availableObservations,
        BigDecimal constituentCoveragePct,
        boolean hasPreInception,
        LocalDate earliestKnownNavDate,
        String evidenceState,
        String metricEvidenceState,
        String attributionRole,
        String notes
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            approximateWeightedReturnIndicatorPct,
            exactAdditiveReturnContributionPct,
            weightedReturnToLinkedAttributionDifferencePct,
            rebalancingInteractionPct,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            expectedObservations,
            availableObservations,
            constituentCoveragePct,
            hasPreInception,
            earliestKnownNavDate,
            evidenceState,
            metricEvidenceState,
            attributionRole,
            notes,
            exactAdditiveReturnContributionPct != null
                ? (exactAdditiveReturnContributionPct.doubleValue() > 0 ? "POSITIVE" : (exactAdditiveReturnContributionPct.doubleValue() < 0 ? "NEGATIVE" : "ZERO"))
                : "UNDEFINED",
            "RESILIENT_LOWER_LOSS_CONTRIBUTOR".equals(attributionRole) || "POSITIVE_CONTRIBUTOR".equals(attributionRole),
            "RESILIENT_LOWER_LOSS_CONTRIBUTOR".equals(attributionRole)
                ? "Exhibited lower cumulative loss than the portfolio proxy and loss drag < 1.0% pts. Mathematical lower loss drag in this specific sample; does NOT establish positive return, downside protection, or superior manager skill."
                : ("POSITIVE_CONTRIBUTOR".equals(attributionRole)
                    ? "Generated positive return contribution to the portfolio proxy during this stress period."
                    : ("PRIMARY_DETRACTOR".equals(attributionRole)
                        ? "Primary loss contributor with largest negative linked contribution or attribution drag exceeding -2.0% pts."
                        : ("MODERATE_DETRACTOR".equals(attributionRole)
                            ? "Moderate loss contributor with attribution drag between -1.0% and -2.0% pts."
                            : "Attribution role descriptive of historical episode sample."))),
            availableObservations != null ? availableObservations : alignedObservationCount,
            expectedObservations != null && availableObservations != null ? Math.max(0, expectedObservations - availableObservations) : 0,
            hasPreInception && expectedObservations != null && availableObservations != null ? Math.max(0, expectedObservations - availableObservations) : 0,
            !hasPreInception && expectedObservations != null && availableObservations != null ? Math.max(0, expectedObservations - availableObservations) : 0,
            0
        );
    }

    /**
     * Backward-compatible 23-parameter constructor (Task 27 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal approximateWeightedReturnIndicatorPct,
        BigDecimal exactAdditiveReturnContributionPct,
        BigDecimal weightedReturnToLinkedAttributionDifferencePct,
        BigDecimal rebalancingInteractionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        Integer expectedObservations,
        Integer availableObservations,
        BigDecimal constituentCoveragePct,
        boolean hasPreInception,
        LocalDate earliestKnownNavDate,
        String evidenceState,
        String attributionRole,
        String notes
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            approximateWeightedReturnIndicatorPct,
            exactAdditiveReturnContributionPct,
            weightedReturnToLinkedAttributionDifferencePct,
            rebalancingInteractionPct,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            expectedObservations,
            availableObservations,
            constituentCoveragePct,
            hasPreInception,
            earliestKnownNavDate,
            evidenceState,
            "SUFFICIENT_EVIDENCE".equals(evidenceState) ? "DESCRIPTIVE_SAMPLE_ONLY" : evidenceState,
            attributionRole,
            notes
        );
    }

    /**
     * Backward-compatible 17-parameter constructor (Task 24 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal approximateWeightedReturnIndicatorPct,
        BigDecimal exactAdditiveReturnContributionPct,
        BigDecimal rebalancingInteractionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        String evidenceState,
        String attributionRole,
        String notes
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            approximateWeightedReturnIndicatorPct,
            exactAdditiveReturnContributionPct,
            rebalancingInteractionPct,
            rebalancingInteractionPct,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            alignedObservationCount,
            alignedObservationCount,
            BigDecimal.valueOf(100.0),
            false,
            null,
            evidenceState,
            attributionRole,
            notes
        );
    }

    /**
     * Backward-compatible 14-parameter constructor (Task 23 compatibility).
     */
    public FundStressContributionDto(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String category,
        BigDecimal currentAllocationWeightPct,
        BigDecimal fundCumulativeReturnPct,
        BigDecimal weightedReturnContributionPct,
        BigDecimal fundMaxDrawdownPct,
        LocalDate fundPeakDate,
        LocalDate fundTroughDate,
        Integer alignedObservationCount,
        String evidenceState,
        String attributionRole,
        String notes
    ) {
        this(
            schemeOptionId,
            fundName,
            amfiCode,
            category,
            currentAllocationWeightPct,
            fundCumulativeReturnPct,
            weightedReturnContributionPct,
            weightedReturnContributionPct,
            weightedReturnContributionPct,
            BigDecimal.ZERO,
            fundMaxDrawdownPct,
            fundPeakDate,
            fundTroughDate,
            alignedObservationCount,
            evidenceState,
            attributionRole,
            notes
        );
    }

    public static String determineStandaloneRole(BigDecimal cumReturn, Boolean outperformed, boolean hasPreInception) {
        if (hasPreInception || cumReturn == null) {
            return "STANDALONE_HISTORY_TRUNCATED";
        }
        if (cumReturn.compareTo(BigDecimal.ZERO) > 0) {
            return "STANDALONE_POSITIVE_RETURN";
        }
        if (Math.abs(cumReturn.doubleValue()) < 0.0001) {
            return "STANDALONE_NEUTRAL";
        }
        if (Boolean.TRUE.equals(outperformed)) {
            return "STANDALONE_LOWER_LOSS_THAN_PROXY";
        }
        return "STANDALONE_HIGHER_LOSS_THAN_PROXY";
    }

    public static String determineStandaloneRole(
        BigDecimal fundCumReturnPct,
        BigDecimal portCumReturnPct,
        BigDecimal bmCumReturnPct,
        boolean isWorstStandalone,
        boolean isBestStandalone
    ) {
        if (fundCumReturnPct == null) {
            return "STANDALONE_HISTORY_TRUNCATED";
        }
        if (fundCumReturnPct.compareTo(BigDecimal.ZERO) > 0) {
            return "STANDALONE_POSITIVE_RETURN";
        }
        if (isWorstStandalone) {
            return "STANDALONE_WORST_RETURN_AMONG_CONSTITUENTS";
        }
        if (isBestStandalone) {
            return "STANDALONE_BEST_RETURN_AMONG_CONSTITUENTS";
        }
        if (portCumReturnPct != null && fundCumReturnPct.compareTo(portCumReturnPct) > 0) {
            return "STANDALONE_LOWER_LOSS_THAN_PROXY";
        }
        return "STANDALONE_HIGHER_LOSS_THAN_PROXY";
    }
}
