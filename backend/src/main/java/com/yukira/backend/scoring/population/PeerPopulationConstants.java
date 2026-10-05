package com.yukira.backend.scoring.population;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Frozen constants for the canonical empirical peer-population calibration workflow.
 *
 * Governance:
 *  - The analysis window, knowledge cutoff, methodology tag and canonical metric set are
 *    declared here exactly once so that peer NAV ingestion, peer metric generation and the
 *    empirical distribution evaluation cannot drift apart.
 *  - These values are configuration, not methodology. No financial mathematics is expressed here.
 */
public final class PeerPopulationConstants {

    private PeerPopulationConstants() {}

    /**
     * Historical NAV ingestion window requested for peer data loading (from 2021-01-01 onward).
     * This precedes the canonical 3Y analytical window (ANALYSIS_START) which begins on 2021-01-15
     * to match the risk-free rate series availability and 3Y anniversary.
     */
    public static final LocalDate INGESTION_START = LocalDate.of(2021, 1, 1);
    public static final LocalDate INGESTION_END   = LocalDate.of(2024, 1, 15);

    /** Canonical Phase 2H analysis window for the Flexi Cap Direct Growth peer cohort (exactly 3 years ending on ANALYSIS_END). */
    public static final LocalDate ANALYSIS_START = LocalDate.of(2021, 1, 15);
    public static final LocalDate ANALYSIS_END   = LocalDate.of(2024, 1, 15);

    /**
     * Point-in-Time knowledge cutoff: end of the analysis day in IST.
     * No observation published after this instant may enter a calibration computed as of ANALYSIS_END.
     */
    public static final OffsetDateTime KNOWLEDGE_CUTOFF = OffsetDateTime.of(
        ANALYSIS_END.atTime(23, 59, 59, 999_000_000),
        ZoneOffset.ofHoursMinutes(5, 30)
    );

    /** Registered TRI benchmark used for every relative / capture / risk-adjusted peer metric. */
    public static final String CANONICAL_BENCHMARK_CODE = "NIFTY_500_TRI";

    /** Registered methodology version tag used for peer calibration calculation runs. */
    public static final String CALIBRATION_METHODOLOGY_TAG = "APPROVED_M2N";

    /** Minimum NAV observations for a fund to qualify for empirical distribution inclusion. */
    public static final int MIN_OBS_THRESHOLD = 700;

    /** Minimum qualifying peer count required for statistically meaningful percentile calibration. */
    public static final int MIN_COHORT_FOR_SIGNIFICANCE = 25;

    /**
     * Canonical score input metric set. This is the exact, closed set of quantitative metrics
     * consumed by YUKIRA_SCORE_V1 (see {@code ScoreMethodologyConfig#getDimensions()}).
     *
     * Explicit governance notes:
     *  - RET-02 (Simple Period Return) is NOT a score input and is NOT part of the peer
     *    calibration set. Peer calibration is executed on the canonical score inputs only.
     *  - MKT-06, REL-04, REL-05, REL-06 and RAT-05 are deprecated aliases or unapproved
     *    candidates and are forbidden in this workflow.
     */
    public static final List<String> CANONICAL_PEER_SCORE_METRIC_CODES = List.of(
        "RET-03", "RET-07",
        "RSK-01", "RSK-02", "RSK-03",
        "REL-02", "RAT-04",
        "MKT-01", "MKT-05", "MKT-02"
    );

    /** Engine parameters that must accompany every peer calibration calculation run. */
    public static final double PERIODS_PER_YEAR = 252.0;
    public static final int MIN_DOWNSIDE_OBSERVATIONS = 100;

    // -------------------------------------------------------------------------
    // Epistemic readiness ladder. These states are deliberately distinct and must
    // never be collapsed into one another.
    // -------------------------------------------------------------------------
    public static final String READINESS_NOT_LOADED       = "NOT_LOADED";
    public static final String READINESS_LOADED           = "LOADED";
    public static final String READINESS_ELIGIBLE         = "ELIGIBLE";
    public static final String READINESS_CALCULATED       = "CALCULATED";
    public static final String READINESS_EMPIRICALLY_READY              = "EMPIRICALLY_READY";
    public static final String READINESS_EMPIRICALLY_READY_FOR_RESEARCH = "EMPIRICALLY_READY_FOR_RESEARCH";
    public static final String STATUS_PROVISIONAL_INSUFFICIENT_DATA     = "PROVISIONAL_INSUFFICIENT_DATA";
    public static final String READINESS_VALIDATED                      = "VALIDATED";
    public static final String READINESS_APPROVED                       = "APPROVED";
}
