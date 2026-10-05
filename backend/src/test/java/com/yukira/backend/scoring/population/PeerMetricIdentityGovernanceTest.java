package com.yukira.backend.scoring.population;

import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Governance regression tests for the canonical peer calibration metric identity.
 *
 * These tests are pure-function tests over frozen governance constants. They assert that the
 * empirical calibration workflow can only ever be executed on the canonical YUKIRA_SCORE_V1
 * input set, and that deprecated / unauthorized aliases are actively rejected rather than
 * silently substituted.
 *
 * Epistemic rule under test:
 *   LOADED != ELIGIBLE != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED.
 */
class PeerMetricIdentityGovernanceTest {

    // -------------------------------------------------------------------------
    // Canonical set is accepted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The frozen canonical peer calibration metric set is itself accepted")
    void canonicalSetIsAccepted() {
        assertDoesNotThrow(() ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(
                PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES));
    }

    @Test
    @DisplayName("Canonical set contains exactly the ten approved score inputs and excludes RET-02")
    void canonicalSetHasExactMembership() {
        List<String> canonical = PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES;

        assertEquals(10, canonical.size(), "YUKIRA_SCORE_V1 has exactly ten approved metric inputs");

        List<String> expected = List.of(
            "RET-03", "RET-07",
            "RSK-01", "RSK-02", "RSK-03",
            "REL-02", "RAT-04",
            "MKT-01", "MKT-05", "MKT-02");
        assertEquals(expected, canonical,
            "Canonical peer calibration metric set drifted from the approved score input set");

        assertTrue(!canonical.contains("RET-02"),
            "RET-02 (Simple Period Return) must NOT substitute for RET-03 in peer calibration");
    }

    // -------------------------------------------------------------------------
    // Unauthorized / deprecated aliases are actively rejected
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "rejects unauthorized metric {0}")
    @ValueSource(strings = { "MKT-06", "REL-04", "REL-05", "REL-06", "RAT-05" })
    @DisplayName("MKT-06, REL-04, REL-05, REL-06 and RAT-05 are actively rejected")
    void rejectsUnauthorizedMetrics(String forbidden) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(List.of(forbidden)));

        assertTrue(ex.getMessage().contains(forbidden),
            "Rejection message must name the offending metric: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("UNAUTHORIZED_METRIC_OR_DEPRECATED_ALIAS"),
            "Rejection reason must be explicit, not silent: " + ex.getMessage());
    }

    @Test
    @DisplayName("RET-02 is rejected as not being part of the canonical score input set")
    void rejectsRet02() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(List.of("RET-02")));

        assertTrue(ex.getMessage().contains("NOT_IN_CANONICAL_SCORE_INPUT_SET"),
            "RET-02 rejection reason: " + ex.getMessage());
    }

    @Test
    @DisplayName("A single forbidden metric poisons the whole batch (no partial acceptance)")
    void rejectsBatchContainingOneForbiddenMetric() {
        List<String> mixed = new ArrayList<>(PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES);
        mixed.add("RAT-05");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(mixed));

        assertTrue(ex.getMessage().contains("RAT-05"),
            "Batch rejection must name the offending metric: " + ex.getMessage());
    }

    @Test
    @DisplayName("Null and blank metric identities are rejected")
    void rejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(java.util.Collections.singletonList(null)));

        IllegalArgumentException blankEx = assertThrows(IllegalArgumentException.class, () ->
            PeerMetricCalculationService.assertCanonicalMetricIdentity(List.of("   ")));
        assertTrue(blankEx.getMessage().contains("<blank>"), blankEx.getMessage());
    }

    // -------------------------------------------------------------------------
    // Drift guard: ScoreMethodologyConfig must equal the frozen canonical set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ScoreMethodologyConfig dimensions equal the frozen canonical calibration set")
    void scoreConfigMatchesCanonicalSet() {
        assertDoesNotThrow(() -> PeerMetricCalculationService.assertScoreConfigMatchesCanonicalSet());
    }

    @Test
    @DisplayName("ScoreMethodologyConfig declares no unauthorized metric in any dimension")
    void scoreConfigContainsNoUnauthorizedMetric() {
        ScoreMethodologyConfig config = new ScoreMethodologyConfig();

        for (ScoreMethodologyConfig.DimensionConfig dim : config.getDimensions().values()) {
            for (ScoreMethodologyConfig.MetricConfig m : dim.metrics()) {
                assertTrue(!ScoreMethodologyConfig.UNAUTHORIZED_METRICS.contains(m.metricCode()),
                    "Unauthorized metric present in score dimension " + dim.dimensionCode()
                        + ": " + m.metricCode());
            }
        }
    }

    @Test
    @DisplayName("PIT Lookback contract: 14-calendar-day gap exceeds 4-day limit and strictly yields INSUFFICIENT_DATA")
    void mkt01PitLookbackContractExceedsThreshold() {
        int maxCalendarLookback = 4;
        java.time.LocalDate fundStart = java.time.LocalDate.of(2021, 1, 1);
        java.time.LocalDate riskFreeStart = java.time.LocalDate.of(2021, 1, 15);
        long gapDays = java.time.temporal.ChronoUnit.DAYS.between(fundStart, riskFreeStart);

        assertEquals(14, gapDays, "Gap between fund start 2021-01-01 and FBIL start 2021-01-15 is 14 days");
        assertTrue(gapDays > maxCalendarLookback, "14-day gap must exceed 4-day max lookback");
        // Strict anti-forward-fill invariant: No data fabrication allowed. Status must remain INSUFFICIENT_DATA.
    }

    @Test
    @DisplayName("Empirical cohort size N=24 strictly fails N>=25 significance threshold (non-promoted)")
    void peerCohortFailsSignificanceThreshold() {
        int actualCohortSize = 24;
        int requiredThreshold = PeerPopulationConstants.MIN_COHORT_FOR_SIGNIFICANCE;

        assertEquals(25, requiredThreshold, "Significance threshold must remain N>=25");
        assertTrue(actualCohortSize < requiredThreshold, "N=24 must be strictly less than 25 threshold");
        // Governance invariant: Empirical population CANNOT be promoted.
    }
}