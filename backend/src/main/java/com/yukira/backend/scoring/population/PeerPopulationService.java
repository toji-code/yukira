package com.yukira.backend.scoring.population;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.normalization.Direction;
import com.yukira.backend.scoring.normalization.ReferenceDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Evaluates readiness of the mutual fund universe and historical NAV foundation for transitioning
 * from the provisional reference population to empirically measured peer distributions.
 *
 * CRITICAL GOVERNANCE CHANGE (this revision):
 *  The previous revision of this service re-implemented RET-02 (Simple Period Return) in Java
 *  using BigDecimal arithmetic and published it as the "empirical peer distribution". That was
 *  a direct violation of AGENTS.md Section 6 (all financial metrics must originate from a
 *  verified mathematical algorithm in {@code backend/} or {@code quant-engine/}) and of the
 *  canonical score input contract (RET-02 is NOT a YUKIRA_SCORE_V1 input; RET-03 is).
 *
 *  This revision performs ZERO financial mathematics. Every value in every empirical
 *  distribution is a persisted, canonical {@code metric_result} produced by the Python
 *  quantitative kernel through {@code CalculationOrchestratorService}. The only arithmetic
 *  performed here is descriptive cross-sectional statistics (count, min, percentiles, mean,
 *  sample standard deviation) over those already-computed canonical values; these are
 *  calibration descriptors, not methodology metrics.
 *
 *  Epistemic rules:
 *  - Observations are selected strictly under the PIT contract:
 *      effective_date <= analysis_cutoff AND availability_time <= knowledge_cutoff
 *  - No interpolation, no forward-fill, no synthetic or fabricated observation.
 *  - Outliers are NEVER auto-deleted; every exclusion carries an explicit reason.
 *  - Provisional population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is never replaced here.
 *  - LOADED != ELIGIBLE != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED.
 */
@Service
@SuppressWarnings("null")
public class PeerPopulationService {

    private static final Logger log = LoggerFactory.getLogger(PeerPopulationService.class);

    private static final int MIN_OBS_THRESHOLD = PeerPopulationConstants.MIN_OBS_THRESHOLD;
    private static final int MIN_COHORT_FOR_SIGNIFICANCE = PeerPopulationConstants.MIN_COHORT_FOR_SIGNIFICANCE;
    private static final LocalDate ANALYSIS_START = PeerPopulationConstants.ANALYSIS_START;
    private static final LocalDate ANALYSIS_END = PeerPopulationConstants.ANALYSIS_END;

    private static final String RISK_FREE_CODE = "FBIL_91D_TBILL";

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final MetricResultRepository metricResultRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final ScoreMethodologyConfig methodologyConfig;

    public PeerPopulationService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        MetricResultRepository metricResultRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        ScoreMethodologyConfig methodologyConfig
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.metricResultRepository = metricResultRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.methodologyConfig = methodologyConfig != null ? methodologyConfig : new ScoreMethodologyConfig();
    }

    // -------------------------------------------------------------------------
    // Lightweight head-count readiness evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates whether empirical reference distributions can replace the provisional population.
     * NAV-coverage head-count only; no distribution computation.
     */
    public PeerPopulationEvaluationReport evaluatePopulation(PeerPopulationCriteria criteria) {
        List<SchemeOption> matchingOptions = findCandidateOptions(criteria);

        long candidateCount = matchingOptions.size();
        long sufficientNavCount = 0;

        for (SchemeOption opt : matchingOptions) {
            long obsCount = navObservationRepository.countBySchemeOptionId(opt.getId());
            if (obsCount >= criteria.minRequiredObservations()) {
                sufficientNavCount++;
            }
        }

        boolean ready = sufficientNavCount >= MIN_COHORT_FOR_SIGNIFICANCE;

        List<String> dataGaps = buildDataGapMessages(candidateCount, sufficientNavCount, criteria);

        return new PeerPopulationEvaluationReport(
            criteria.populationCategory(),
            ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
            ready ? "DATA_SUFFICIENT_FOR_STUDY" : "PROVISIONAL_INSUFFICIENT_DATA",
            candidateCount,
            sufficientNavCount,
            MIN_COHORT_FOR_SIGNIFICANCE,
            ready,
            dataGaps,
            "Provisional population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is retained as the authoritative reference. "
            + "IMPLEMENTED != VALIDATED != APPROVED."
        );
    }

    // -------------------------------------------------------------------------
    // Full empirical evaluation: coverage + canonical MetricResults + distributions
    // -------------------------------------------------------------------------

    /**
     * Produces the comprehensive machine-readable cohort report:
     *  - Per-fund NAV coverage summaries over the canonical window (LOADED / ELIGIBLE).
     *  - Per-metric readiness ladder derived from persisted canonical MetricResults.
     *  - Empirical distributions (N, min, P05, P25, median, P75, P95, max, mean, sample stdev)
     *    built exclusively from CALCULATED canonical MetricResults of ELIGIBLE peers.
     *  - Explicit missing / excluded accounting with per-fund reasons. No silent drops.
     *  - Verified benchmark + risk-free coverage over the same window.
     *  - Provisional-versus-empirical evidence diff. The provisional population is RETAINED.
     *
     * Does NOT replace the provisional reference population.
     * Does NOT write to {@code analytical_score}.
     */
    public PeerPopulationEvaluationReport generateFullCohortReport(PeerPopulationCriteria criteria) {
        log.info("Generating canonical empirical cohort report for: {} {} {} window {}..{}",
            criteria.populationCategory(), criteria.planType(), criteria.optionType(), ANALYSIS_START, ANALYSIS_END);

        // Defensive mutable copy: repository-backed Specification results may be immutable,
        // and this list is sorted in place below for deterministic audit ordering.
        List<SchemeOption> candidates = new ArrayList<>(findCandidateOptions(criteria));
        candidates.sort(Comparator.comparing(SchemeOption::getId));

        // ── Coverage: NAV LOADED / ELIGIBLE per fund ──────────────────────────
        List<PeerFundCoverageSummary> coverageSummaries = new ArrayList<>();
        List<Long> eligiblePeerIds = new ArrayList<>();
        long sufficientCount = 0;
        long partialCount = 0;
        long insufficientCount = 0;
        long peersWithAnyNav = 0;

        for (SchemeOption opt : candidates) {
            long totalObs = navObservationRepository.countBySchemeOptionId(opt.getId());
            if (totalObs > 0) {
                peersWithAnyNav++;
            }

            String amcCode = amcCode(opt);
            String schemeName = schemeName(opt);

            String status;
            LocalDate firstDate = null;
            LocalDate lastDate = null;

            if (totalObs >= MIN_OBS_THRESHOLD) {
                status = "SUFFICIENT_HISTORY";
                sufficientCount++;
                // The subject fund is a member of the catalog cohort but must never enter its own
                // peer reference population.
                if (!Objects.equals(opt.getId(), PeerMetricCalculationService.PILOT_SUBJECT_SCHEME_OPTION_ID)) {
                    eligiblePeerIds.add(opt.getId());
                }
            } else if (totalObs > 0) {
                status = "PARTIAL_HISTORY";
                partialCount++;
            } else {
                status = "INSUFFICIENT_HISTORY";
                insufficientCount++;
            }

            if (totalObs > 0) {
                List<NavObservation> ingestionObsList = navObservationRepository
                    .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                        opt.getId(), PeerPopulationConstants.INGESTION_START, PeerPopulationConstants.INGESTION_END);
                if (!ingestionObsList.isEmpty()) {
                    firstDate = ingestionObsList.get(0).getEffectiveDate();
                    lastDate = ingestionObsList.get(ingestionObsList.size() - 1).getEffectiveDate();
                }
            }

            coverageSummaries.add(new PeerFundCoverageSummary(
                opt.getId(),
                opt.getAmfiCode(),
                opt.getIsin(),
                amcCode,
                schemeName,
                (int) totalObs,
                firstDate,
                lastDate,
                status,
                0,
                0,
                totalObs >= MIN_OBS_THRESHOLD,
                null
            ));
        }

        // ── Canonical MetricResult read model ─────────────────────────────────
        BenchmarkCoverageInfo benchmarkCoverage = buildBenchmarkCoverageInfo(criteria);

        List<String> metricCodes = PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES;
        Long benchmarkId = benchmarkCoverage.benchmarkId();

        Map<Long, Map<String, PeerMetricResultProjection>> metricResultsByPeer =
            loadCanonicalMetricResults(eligiblePeerIds, benchmarkId, metricCodes);

        long excludedByCoverage = candidates.size() - eligiblePeerIds.size();

        // ── Per-metric empirical distributions + readiness ladder ──────────────
        List<MetricEmpiricalReadiness> metricReadiness = new ArrayList<>();
        Map<String, EmpiricalDistributionSummary> distributions = new LinkedHashMap<>();
        boolean anyMetricEmpiricallyReady = false;

        for (String metricCode : metricCodes) {
            MetricEmpiricalReadiness readiness = buildMetricReadiness(
                metricCode,
                candidates.size(),
                peersWithAnyNav,
                eligiblePeerIds.size(),
                (int) excludedByCoverage,
                metricResultsByPeer,
                benchmarkCoverage
            );
            metricReadiness.add(readiness);
            distributions.put(metricCode, readiness.empiricalDistribution());
            if (readiness.empiricallyReady()) {
                anyMetricEmpiricallyReady = true;
            }
        }

        // ── Readiness ladder ──────────────────────────────────────────────────
        boolean allMetricsEmpiricallyReady = !metricReadiness.isEmpty()
            && metricReadiness.stream().allMatch(MetricEmpiricalReadiness::empiricallyReady);
        boolean empiricallyReady = allMetricsEmpiricallyReady
            && eligiblePeerIds.size() >= MIN_COHORT_FOR_SIGNIFICANCE;

        List<String> readinessLadder = List.of(
            "LOADED            : " + peersWithAnyNav + " of " + candidates.size()
                + " cohort options carry >= 1 persisted NAV observation in the canonical ledger.",
            "ELIGIBLE          : " + sufficientCount + " cohort options carry >= " + MIN_OBS_THRESHOLD
                + " persisted NAV observations; " + eligiblePeerIds.size()
                + " qualify as calibration peers after excluding the pilot subject fund.",
            "CALCULATED        : canonical quant-engine MetricResults persisted under as_of="
                + ANALYSIS_END + ", knowledge_cutoff=" + PeerPopulationConstants.KNOWLEDGE_CUTOFF
                + ", benchmark=" + criteria.benchmarkCode() + ".",
            "EMPIRICALLY_READY : " + (empiricallyReady ? "EMPIRICALLY_READY_FOR_RESEARCH" : "FALSE")
                + " (requires >= " + MIN_COHORT_FOR_SIGNIFICANCE
                + " peers with a CALCULATED canonical MetricResult for all canonical score metrics; current N="
                + eligiblePeerIds.size() + ").",
            "VALIDATED         : FALSE. Multi-cycle empirical study and governance authorization "
                + "have not been performed. Not asserted by this service.",
            "APPROVED          : FALSE. Formal project review committee authorization has not been "
                + "granted. Not asserted by this service."
        );

        // ── Data gaps ─────────────────────────────────────────────────────────
        List<String> dataGaps = new ArrayList<>();
        dataGaps.add(String.format(
            "Catalog universe: %d candidates | LOADED (any NAV): %d | ELIGIBLE (>=%d obs): %d | "
            + "Partial (1..%d obs): %d | Zero observations: %d | Calibration peers after excluding "
            + "the pilot subject fund: %d.",
            candidates.size(), peersWithAnyNav, MIN_OBS_THRESHOLD, sufficientCount,
            MIN_OBS_THRESHOLD - 1, partialCount, insufficientCount, eligiblePeerIds.size()));

        dataGaps.add(String.format(
            "Window specification: Historical NAV ingestion window is %s to %s (749 total observations per qualifying peer; "
            + "10 pre-analytical trading sessions from 2021-01-01 to 2021-01-14). Canonical 3Y analytical metric window is "
            + "%s to %s (739 observations per qualifying peer; aligns with FBIL 91D T-Bill risk-free series inception and "
            + "canonical 3-year anniversary ending 2024-01-15). Benchmark NIFTY_500_TRI contains 743 trading sessions "
            + "(4 Muhurat/exchange-only sessions where mutual funds publish no NAV); inner-joining yields 739 aligned dates "
            + "and 738 paired daily return periods.",
            PeerPopulationConstants.INGESTION_START, PeerPopulationConstants.INGESTION_END,
            ANALYSIS_START, ANALYSIS_END));

        if (!empiricallyReady) {
            dataGaps.add(String.format(
                "EMPIRICALLY READY = FALSE. Eligible calibration peers (%d) and/or per-metric "
                + "CALCULATED peer counts are below the minimum statistical requirement of %d.",
                eligiblePeerIds.size(), MIN_COHORT_FOR_SIGNIFICANCE));
            dataGaps.addAll(benchmarkCoverage.coverageGaps());
        }

        for (MetricEmpiricalReadiness mr : metricReadiness) {
            EmpiricalDistributionSummary d = mr.empiricalDistribution();
            if (d.sampleSize() == 0) {
                dataGaps.add(String.format(
                    "%s: sample size 0. %d eligible peers, %d excluded by NAV coverage, %d missing. "
                    + "No percentile may be asserted.",
                    mr.metricCode(), mr.eligiblePeerCount(), mr.excludedByNavCoverageCount(), mr.missingPeerCount()));
            } else {
                dataGaps.add(String.format(
                    "%s: N=%d (minimum %d). min=%.6f P05=%.6f P25=%.6f median=%.6f P75=%.6f P95=%.6f "
                    + "max=%.6f mean=%.6f sampleStdev=%.6f. EMPIRICALLY_READY=%s.",
                    mr.metricCode(), d.sampleSize(), MIN_COHORT_FOR_SIGNIFICANCE,
                    d.min(), d.p05(), d.p25(), d.median(), d.p75(), d.p95(), d.max(), d.mean(),
                    d.standardDeviation() != null ? d.standardDeviation() : Double.NaN,
                    mr.empiricallyReady()));
            }
        }

        long sufficientMinusSubject = Math.max(0, sufficientCount - 1);

        return new PeerPopulationEvaluationReport(
            criteria.populationCategory(),
            ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
            empiricallyReady ? PeerPopulationConstants.READINESS_EMPIRICALLY_READY_FOR_RESEARCH
                             : PeerPopulationConstants.STATUS_PROVISIONAL_INSUFFICIENT_DATA,
            (long) candidates.size(),
            sufficientCount,
            partialCount,
            insufficientCount,
            excludedByCoverage,
            MIN_COHORT_FOR_SIGNIFICANCE,
            empiricallyReady,
            coverageSummaries,
            distributions,
            metricReadiness,
            benchmarkCoverage,
            readinessLadder,
            metricCodes,
            ANALYSIS_START,
            ANALYSIS_END,
            PeerPopulationConstants.KNOWLEDGE_CUTOFF,
            (int) peersWithAnyNav,
            (int) eligiblePeerIds.size(),
            (int) sufficientMinusSubject,
            dataGaps,
            "Provisional population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is RETAINED and was not replaced, "
            + "modified or deactivated. Every distribution value is a persisted canonical MetricResult computed by "
            + "the Python quant engine; no financial metric was recomputed in Java. No analytical_score row was "
            + "created, modified or deleted. "
            + "LOADED != ELIGIBLE != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED. "
            + "VALIDATED = FALSE. APPROVED = FALSE."
        );
    }

    // =========================================================================
    // Canonical MetricResult read model
    // =========================================================================

    private Map<Long, Map<String, PeerMetricResultProjection>> loadCanonicalMetricResults(
        List<Long> eligiblePeerIds, Long benchmarkId, List<String> metricCodes) {

        Map<Long, Map<String, PeerMetricResultProjection>> byPeer = new TreeMap<>();
        for (Long id : eligiblePeerIds) {
            byPeer.put(id, new LinkedHashMap<>());
        }

        if (metricResultRepository == null || benchmarkId == null || eligiblePeerIds.isEmpty()) {
            return byPeer;
        }

        List<PeerMetricResultProjection> rows = metricResultRepository.findCalibrationMetricResults(
            eligiblePeerIds,
            PeerMetricCalculationService.PILOT_SUBJECT_SCHEME_OPTION_ID,
            benchmarkId,
            ANALYSIS_END,
            PeerPopulationConstants.KNOWLEDGE_CUTOFF,
            metricCodes
        );

        for (PeerMetricResultProjection row : rows) {
            Map<String, PeerMetricResultProjection> perMetric =
                byPeer.computeIfAbsent(row.getSchemeOptionId(), k -> new LinkedHashMap<>());

            boolean isCalculated = "CALCULATED".equals(row.getCalculationStatus()) && row.getNumericValue() != null;
            PeerMetricResultProjection existing = perMetric.get(row.getMetricCode());

            if (existing == null) {
                perMetric.put(row.getMetricCode(), row);
            } else {
                boolean existingCalculated = "CALCULATED".equals(existing.getCalculationStatus()) && existing.getNumericValue() != null;
                // A valid CALCULATED result with non-null value always takes precedence over an INSUFFICIENT_DATA or failed run
                if (isCalculated && !existingCalculated) {
                    perMetric.put(row.getMetricCode(), row);
                } else if (isCalculated == existingCalculated && row.getCalculationRunId() != null
                    && existing.getCalculationRunId() != null && row.getCalculationRunId() > existing.getCalculationRunId()) {
                    perMetric.put(row.getMetricCode(), row);
                }
            }
        }
        return byPeer;
    }

    // =========================================================================
    // Per-metric readiness
    // =========================================================================

    private MetricEmpiricalReadiness buildMetricReadiness(
        String metricCode,
        int cohortCandidateCount,
        long peersWithAnyNav,
        int eligiblePeerCount,
        int excludedByNavCoverageCount,
        Map<Long, Map<String, PeerMetricResultProjection>> metricResultsByPeer,
        BenchmarkCoverageInfo benchmarkCoverage
    ) {
        List<Double> values = new ArrayList<>();
        List<String> missingReasons = new ArrayList<>();
        List<String> exclusionReasons = new ArrayList<>();
        String units = "UNKNOWN";

        int missing = 0;
        for (Map.Entry<Long, Map<String, PeerMetricResultProjection>> peerEntry : metricResultsByPeer.entrySet()) {
            PeerMetricResultProjection row = peerEntry.getValue().get(metricCode);
            if (row == null) {
                missing++;
                missingReasons.add("Peer scheme_option " + peerEntry.getKey()
                    + " has no persisted MetricResult for " + metricCode
                    + " under the canonical calibration configuration. Counted as MISSING, never imputed.");
                continue;
            }
            if (!"CALCULATED".equals(row.getCalculationStatus())) {
                missing++;
                missingReasons.add("Peer scheme_option " + peerEntry.getKey() + " " + metricCode
                    + " status=" + row.getCalculationStatus() + ". Counted as MISSING, never imputed.");
                continue;
            }
            if (row.getNumericValue() == null) {
                missing++;
                missingReasons.add("Peer scheme_option " + peerEntry.getKey() + " " + metricCode
                    + " is CALCULATED with a NULL numeric value. Counted as MISSING, never imputed.");
                continue;
            }
            if (row.getUnits() != null) {
                units = row.getUnits();
            }
            values.add(row.getNumericValue().doubleValue());
        }

        int excluded = Math.max(0, cohortCandidateCount - eligiblePeerCount);

        String metricName = resolveMetricName(metricCode);
        String windowLabel = "Peer cohort, " + ANALYSIS_START + " to " + ANALYSIS_END
            + ", knowledge cutoff " + PeerPopulationConstants.KNOWLEDGE_CUTOFF;

        EmpiricalDistributionSummary distribution =
            buildDistributionSummary(metricCode, metricName, units, windowLabel, values, missing, excluded, missingReasons);

        ReferenceDistribution provisional = methodologyConfig.getDistribution(metricCode);
        ProvisionalVsEmpiricalDiff diff = buildProvisionalVsEmpiricalDiff(provisional, distribution);

        String peerLoadState = peersWithAnyNav == 0 ? PeerPopulationConstants.READINESS_NOT_LOADED
            : PeerPopulationConstants.READINESS_LOADED;
        String peerEligibilityState = eligiblePeerCount == 0 ? PeerPopulationConstants.READINESS_NOT_LOADED
            : PeerPopulationConstants.READINESS_ELIGIBLE;
        String canonicalCalculationState = values.isEmpty() ? PeerPopulationConstants.READINESS_LOADED
            : PeerPopulationConstants.READINESS_CALCULATED;
        boolean empiricallyReady = values.size() >= MIN_COHORT_FOR_SIGNIFICANCE;

        List<String> notes = new ArrayList<>();
        notes.add("Outliers are never auto-deleted. " + excluded
            + " cohort members were excluded from this distribution because they do not meet the "
            + MIN_OBS_THRESHOLD + " NAV observation eligibility threshold or are not yet persisted "
            + "as CALCULATED canonical MetricResults.");
        notes.add("No value was interpolated, imputed, forward-filled or synthesized.");
        notes.add("VALIDATED = FALSE and APPROVED = FALSE. Not asserted by this service.");
        if (provisional == null) {
            notes.add("No provisional reference distribution is configured for " + metricCode
                + "; provisional-vs-empirical diff is not comparable.");
        }

        return new MetricEmpiricalReadiness(
            metricCode,
            metricName,
            units,
            peerLoadState,
            peerEligibilityState,
            canonicalCalculationState,
            empiricallyReady ? PeerPopulationConstants.READINESS_EMPIRICALLY_READY
                             : PeerPopulationConstants.READINESS_CALCULATED,
            empiricallyReady,
            false,
            false,
            cohortCandidateCount,
            (int) peersWithAnyNav,
            eligiblePeerCount,
            excludedByNavCoverageCount,
            values.size(),
            missing,
            excluded,
            MIN_COHORT_FOR_SIGNIFICANCE,
            ANALYSIS_START,
            ANALYSIS_END,
            PeerPopulationConstants.KNOWLEDGE_CUTOFF,
            benchmarkCoverage.benchmarkCode(),
            PeerPopulationConstants.CALIBRATION_METHODOLOGY_TAG,
            distribution,
            diff,
            notes
        );
    }

    private String resolveMetricName(String metricCode) {
        for (ScoreMethodologyConfig.DimensionConfig dim : methodologyConfig.getDimensions().values()) {
            for (ScoreMethodologyConfig.MetricConfig m : dim.metrics()) {
                if (m.metricCode().equals(metricCode)) {
                    return m.metricName();
                }
            }
        }
        return metricCode;
    }

    // =========================================================================
    // Descriptive cross-sectional statistics over canonical MetricResults
    // =========================================================================

    private EmpiricalDistributionSummary buildDistributionSummary(
        String metricCode,
        String metricName,
        String units,
        String windowLabel,
        List<Double> values,
        int missingCount,
        int excludedCount,
        List<String> exclusionReasons
    ) {
        if (values.isEmpty()) {
            return new EmpiricalDistributionSummary(
                metricCode, metricName, units, windowLabel, 0,
                null, null, null, null, null, null, null, null, null,
                missingCount, excludedCount, exclusionReasons
            );
        }

        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int n = sorted.size();

        double min    = sorted.get(0);
        double max    = sorted.get(n - 1);
        double mean   = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double p05    = interpolatedPercentile(sorted, 5.0);
        double p25    = interpolatedPercentile(sorted, 25.0);
        double median = interpolatedPercentile(sorted, 50.0);
        double p75    = interpolatedPercentile(sorted, 75.0);
        double p95    = interpolatedPercentile(sorted, 95.0);

        // Sample standard deviation (Bessel's correction: n-1 denominator)
        double stdDev = Double.NaN;
        if (n >= 2) {
            double sumSq = sorted.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
            stdDev = Math.sqrt(sumSq / (n - 1));
        }

        return new EmpiricalDistributionSummary(
            metricCode, metricName, units, windowLabel, n,
            round8(min),
            round8(p05),
            round8(p25),
            round8(median),
            round8(p75),
            round8(p95),
            round8(max),
            round8(mean),
            Double.isNaN(stdDev) ? null : round8(stdDev),
            missingCount,
            excludedCount,
            exclusionReasons
        );
    }

    /**
     * Linear interpolation percentile (equivalent to NumPy's default method='linear').
     * p in [0, 100].
     */
    private static double interpolatedPercentile(List<Double> sortedValues, double p) {
        int n = sortedValues.size();
        if (n == 0) return Double.NaN;
        if (n == 1) return sortedValues.get(0);

        double index = (p / 100.0) * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, n - 1);
        double fraction = index - lower;

        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }

    private static Double round8(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return BigDecimal.valueOf(v).setScale(8, RoundingMode.HALF_UP).doubleValue();
    }

    // =========================================================================
    // Provisional vs empirical evidence diff (never replaces the provisional set)
    // =========================================================================

    private ProvisionalVsEmpiricalDiff buildProvisionalVsEmpiricalDiff(
        ReferenceDistribution provisional, EmpiricalDistributionSummary empirical) {

        if (provisional == null) {
            return ProvisionalVsEmpiricalDiff.notComparable(
                ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
                "NO_PROVISIONAL_REFERENCE_CONFIGURED");
        }
        if (empirical.sampleSize() == 0) {
            return ProvisionalVsEmpiricalDiff.notComparable(
                ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
                "EMPIRICAL_SAMPLE_EMPTY_PROVISIONAL_RETAINED_UNCHANGED");
        }

        Double pMin = ProvisionalVsEmpiricalDiff.toDouble(provisional.min());
        Double pP10 = ProvisionalVsEmpiricalDiff.toDouble(provisional.p10());
        Double pP25 = ProvisionalVsEmpiricalDiff.toDouble(provisional.p25());
        Double pMed = ProvisionalVsEmpiricalDiff.toDouble(provisional.median());
        Double pP75 = ProvisionalVsEmpiricalDiff.toDouble(provisional.p75());
        Double pP90 = ProvisionalVsEmpiricalDiff.toDouble(provisional.p90());
        Double pMax = ProvisionalVsEmpiricalDiff.toDouble(provisional.max());
        Double pTarget = ProvisionalVsEmpiricalDiff.toDouble(provisional.targetValue());

        Double dP25 = delta(empirical.p25(), pP25);
        Double dMed = delta(empirical.median(), pMed);
        Double dP75 = delta(empirical.p75(), pP75);

        Double provIqr = (pP25 != null && pP75 != null) ? round8(pP75 - pP25) : null;
        Double empIqr = (empirical.p25() != null && empirical.p75() != null)
            ? round8(empirical.p75() - empirical.p25()) : null;

        Double targetDelta = (provisional.direction() == Direction.TARGET_VALUE && pTarget != null)
            ? delta(empirical.median(), pTarget) : null;

        return new ProvisionalVsEmpiricalDiff(
            ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
            provisional.isProvisional(),
            "EVIDENCE_ONLY_PROVISIONAL_RETAINED_NOT_REPLACED",
            provisional.direction() != null ? provisional.direction().name() : null,
            pMin, pP10, pP25, pMed, pP75, pP90, pMax, pTarget,
            empirical.min(), empirical.p25(), empirical.median(), empirical.p75(), empirical.p95(),
            empirical.max(), empirical.mean(), empirical.standardDeviation(), empirical.sampleSize(),
            dP25, dMed, dP75, provIqr, empIqr,
            targetDelta
        );
    }

    private static Double delta(Double empirical, Double provisional) {
        if (empirical == null || provisional == null) return null;
        return round8(empirical - provisional);
    }

    // =========================================================================
    // Verified benchmark + risk-free coverage
    // =========================================================================

    private BenchmarkCoverageInfo buildBenchmarkCoverageInfo(PeerPopulationCriteria criteria) {
        List<String> gaps = new ArrayList<>();

        if (benchmarkRepository == null || benchmarkObservationRepository == null) {
            gaps.add("Benchmark repository unavailable; benchmark coverage NOT VERIFIED.");
            return new BenchmarkCoverageInfo(
                criteria.benchmarkCode(), null, false, null, null, null, 0, MIN_OBS_THRESHOLD,
                null, null, null, 0, ANALYSIS_START, ANALYSIS_END, gaps);
        }

        Optional<Benchmark> maybeBenchmark = benchmarkRepository.findByCode(criteria.benchmarkCode());
        if (maybeBenchmark.isEmpty()) {
            gaps.add("Canonical benchmark " + criteria.benchmarkCode()
                + " is NOT registered. Relative, capture and risk-adjusted peer metrics cannot be calibrated.");
            return new BenchmarkCoverageInfo(
                criteria.benchmarkCode(), null, false, null, null, null, 0, MIN_OBS_THRESHOLD,
                null, null, null, 0, ANALYSIS_START, ANALYSIS_END, gaps);
        }

        Benchmark benchmark = maybeBenchmark.get();

        List<BenchmarkObservation> obs = benchmarkObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(benchmark.getId(), ANALYSIS_END, PeerPopulationConstants.KNOWLEDGE_CUTOFF)
            .stream()
            .filter(o -> !o.getEffectiveDate().isBefore(ANALYSIS_START) && !o.getEffectiveDate().isAfter(ANALYSIS_END))
            .collect(Collectors.toList());
        obs.sort(Comparator.comparing(BenchmarkObservation::getEffectiveDate));

        LocalDate first = obs.isEmpty() ? null : obs.get(0).getEffectiveDate();
        LocalDate last = obs.isEmpty() ? null : obs.get(obs.size() - 1).getEffectiveDate();

        LocalDate rfFirst = null;
        LocalDate rfLast = null;
        int rfCount = 0;
        if (riskFreeObservationRepository != null) {
            List<RiskFreeObservation> rf = riskFreeObservationRepository.findAuthoritativeObservationsBetween(
                RISK_FREE_CODE, ANALYSIS_START, ANALYSIS_END, PeerPopulationConstants.KNOWLEDGE_CUTOFF);
            rfCount = rf.size();
            if (!rf.isEmpty()) {
                rfFirst = rf.get(0).getEffectiveDate();
                rfLast = rf.get(rf.size() - 1).getEffectiveDate();
            }
        }

        if (obs.size() < MIN_OBS_THRESHOLD) {
            gaps.add(String.format(
                "Benchmark %s carries %d PIT-resolved observations over %s..%s, below the %d minimum "
                + "required for paired relative metrics.",
                criteria.benchmarkCode(), obs.size(), ANALYSIS_START, ANALYSIS_END, MIN_OBS_THRESHOLD));
        }
        if (first != null && first.isAfter(ANALYSIS_START)) {
            gaps.add("Benchmark series begins " + first + ", after the declared analysis window start "
                + ANALYSIS_START + ". Trading days between " + ANALYSIS_START + " and " + first.minusDays(1)
                + " are NOT covered. No synthetic backfill was applied.");
        }
        if (rfCount == 0) {
            gaps.add("Risk-free series " + RISK_FREE_CODE + " has no PIT-resolved observations over the window. "
                + "Excess-return metrics (MKT-01, RET-07, REL-02) fall back to the candidate scalar proxy "
                + "and must be read as such.");
        } else if (rfFirst != null && rfFirst.isAfter(ANALYSIS_START)) {
            gaps.add("Risk-free series " + RISK_FREE_CODE + " begins " + rfFirst + ", after the declared "
                + "analysis window start " + ANALYSIS_START + ".");
        }

        return new BenchmarkCoverageInfo(
            criteria.benchmarkCode(),
            benchmark.getId(),
            true,
            benchmark.getReturnVariant(),
            first,
            last,
            obs.size(),
            MIN_OBS_THRESHOLD,
            RISK_FREE_CODE,
            rfFirst,
            rfLast,
            rfCount,
            ANALYSIS_START,
            ANALYSIS_END,
            gaps
        );
    }

    // =========================================================================
    // Shared utilities
    // =========================================================================

    private List<SchemeOption> findCandidateOptions(PeerPopulationCriteria criteria) {
        return schemeOptionRepository.findAll((root, query, cb) -> {
            var planJoin = root.join("plan");
            var schemeJoin = planJoin.join("scheme");

            var predCategory = cb.or(
                cb.like(cb.lower(schemeJoin.get("category")), "%" + criteria.populationCategory().toLowerCase() + "%"),
                cb.like(cb.lower(schemeJoin.get("subcategory")), "%" + criteria.populationCategory().toLowerCase() + "%")
            );
            var predPlan = cb.equal(cb.upper(planJoin.get("planType")), criteria.planType().toUpperCase());
            var predOption = cb.equal(cb.upper(root.get("optionType")), criteria.optionType().toUpperCase());

            return cb.and(predCategory, predPlan, predOption);
        });
    }

    private static String amcCode(SchemeOption option) {
        return option.getPlan() != null && option.getPlan().getScheme() != null
            && option.getPlan().getScheme().getAmc() != null
            ? option.getPlan().getScheme().getAmc().getCode() : "UNKNOWN";
    }

    private static String schemeName(SchemeOption option) {
        return option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName() : "Unknown";
    }

    private List<String> buildDataGapMessages(long candidateCount, long sufficientNavCount,
                                              PeerPopulationCriteria criteria) {
        List<String> dataGaps = new ArrayList<>();
        if (candidateCount == 0) {
            dataGaps.add("No scheme options found in catalog matching category '"
                + criteria.populationCategory() + "'. Run universe ingestion first.");
        } else {
            dataGaps.add(String.format(
                "Found %d candidate options in catalog for '%s' (%s %s), but only %d currently have >= %d historical NAV observations.",
                candidateCount, criteria.populationCategory(), criteria.planType(), criteria.optionType(),
                sufficientNavCount, criteria.minRequiredObservations()));
        }
        if (sufficientNavCount < MIN_COHORT_FOR_SIGNIFICANCE) {
            dataGaps.add(String.format(
                "Cohort size (%d) is below the minimum statistical requirement (%d) for distribution calibration.",
                sufficientNavCount, MIN_COHORT_FOR_SIGNIFICANCE));
            dataGaps.add("Peer historical NAV series must be loaded before canonical MetricResults can be computed across the peer universe.");
            dataGaps.add("Benchmark (" + criteria.benchmarkCode() + " / TRI) historical series must be ingested for relative metrics (Alpha, Beta, Tracking Error).");
            dataGaps.add("Empirical peer distribution calibration methodology is CANDIDATE / UNVALIDATED.");
        }
        return dataGaps;
    }
}
