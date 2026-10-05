package com.yukira.backend.scoring.population;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.dto.analysis.ProfileCalculationRequest;
import com.yukira.backend.ingestion.amfi.PeerHistoricalNavIngestionService;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Generates canonical peer MetricResults for empirical reference-population calibration.
 *
 * GOVERNANCE:
 *  - This service performs NO financial mathematics. It only assembles the canonical
 *    ProfileCalculationRequest and delegates to {@link AnalysisService}, which delegates to
 *    the Python quantitative kernel. Every persisted value therefore carries a
 *    calculation_run manifest, an input_snapshot_sha256 and full input observation lineage.
 *  - The provisional reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is never
 *    modified, replaced or written by this service.
 *  - No analytical_score row is created, updated or deleted by this service.
 *  - DEPRECATED / UNAUTHORIZED metric identities (MKT-06, REL-04, REL-05, REL-06, RAT-05)
 *    are rejected before any engine invocation.
 *  - LOADED != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED.
 */
@Service
@SuppressWarnings("null")
public class PeerMetricCalculationService {

    private static final Logger log = LoggerFactory.getLogger(PeerMetricCalculationService.class);

    /**
     * The subject fund of the pilot (HDFC Flexi Cap Fund - Growth, scheme_option 1) must never
     * enter its own peer reference population.
     */
    public static final Long PILOT_SUBJECT_SCHEME_OPTION_ID = 1L;

    private final AnalysisService analysisService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final NavObservationRepository navObservationRepository;

    public PeerMetricCalculationService(
        AnalysisService analysisService,
        SchemeOptionRepository schemeOptionRepository,
        BenchmarkRepository benchmarkRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.analysisService = analysisService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.navObservationRepository = navObservationRepository;
    }

    // =========================================================================
    // Metric identity governance
    // =========================================================================

    /**
     * Rejects any metric identity that is not part of the canonical YUKIRA_SCORE_V1 input set.
     *
     * @throws IllegalArgumentException when a deprecated alias or unapproved candidate is requested
     */
    public static void assertCanonicalMetricIdentity(List<String> metricCodes) {
        Objects.requireNonNull(metricCodes, "metricCodes must not be null");

        Set<String> canonical = new LinkedHashSet<>(PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES);
        List<String> rejected = new ArrayList<>();

        for (String code : metricCodes) {
            if (code == null || code.isBlank()) {
                rejected.add("<blank>");
                continue;
            }
            String normalized = code.trim().toUpperCase();
            if (ScoreMethodologyConfig.UNAUTHORIZED_METRICS.contains(normalized)) {
                rejected.add(normalized + " (UNAUTHORIZED_METRIC_OR_DEPRECATED_ALIAS)");
            } else if (!canonical.contains(normalized)) {
                rejected.add(normalized + " (NOT_IN_CANONICAL_SCORE_INPUT_SET)");
            }
        }

        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException(
                "Rejected metric identity for peer calibration: " + rejected
                + ". Authorized canonical peer calibration metrics are "
                + PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES + ".");
        }
    }

    /**
     * Verifies that {@link ScoreMethodologyConfig} and the frozen canonical peer metric set
     * agree exactly, and that no unauthorized metric is configured in any score dimension.
     * Guards against silent drift between the score methodology and empirical calibration.
     *
     * @throws IllegalStateException when the two sets diverge
     */
    public static void assertScoreConfigMatchesCanonicalSet() {
        Set<String> fromConfig = new LinkedHashSet<>();
        for (ScoreMethodologyConfig.DimensionConfig dim : new ScoreMethodologyConfig().getDimensions().values()) {
            for (ScoreMethodologyConfig.MetricConfig m : dim.metrics()) {
                if (ScoreMethodologyConfig.UNAUTHORIZED_METRICS.contains(m.metricCode())) {
                    throw new IllegalStateException(
                        "Unauthorized metric configured in score dimension "
                        + dim.dimensionCode() + ": " + m.metricCode());
                }
                fromConfig.add(m.metricCode());
            }
        }
        Set<String> canonical = new LinkedHashSet<>(PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES);
        if (!fromConfig.equals(canonical)) {
            throw new IllegalStateException(
                "Canonical peer calibration metric set has drifted from ScoreMethodologyConfig. "
                + "configOnly=" + difference(fromConfig, canonical)
                + " canonicalOnly=" + difference(canonical, fromConfig));
        }
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    // =========================================================================
    // Canonical peer metric generation
    // =========================================================================

    /**
     * Executes the canonical quantitative pipeline for every eligible peer and persists
     * MetricResults under the calibration configuration declared in
     * {@link PeerPopulationConstants}.
     *
     * Intentionally NOT annotated {@code @Transactional}: every
     * {@code AnalysisService#executeProfileAnalysis} call must own its own transaction so a
     * single fund failure cannot roll back the entire cohort batch.
     */
    public PeerMetricGenerationReport generatePeerMetrics(PeerPopulationCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        assertCanonicalMetricIdentity(PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES);
        assertScoreConfigMatchesCanonicalSet();

        String runId = "RUN-PEERMET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Benchmark benchmark = benchmarkRepository.findByCode(PeerPopulationConstants.CANONICAL_BENCHMARK_CODE)
            .orElseThrow(() -> new IllegalStateException(
                "Canonical TRI benchmark " + PeerPopulationConstants.CANONICAL_BENCHMARK_CODE
                + " is not registered. Relative and capture peer metrics cannot be produced."));

        List<SchemeOption> candidates = findCandidateOptions(criteria);
        candidates.sort(Comparator.comparing(SchemeOption::getId));

        List<SchemeOption> eligiblePeers = new ArrayList<>();
        int excludedByCoverage = 0;
        for (SchemeOption option : candidates) {
            if (Objects.equals(option.getId(), PILOT_SUBJECT_SCHEME_OPTION_ID)) {
                continue; // The subject fund is never a member of its own reference population.
            }
            long obs = navObservationRepository.countBySchemeOptionId(option.getId());
            if (obs >= PeerPopulationConstants.MIN_OBS_THRESHOLD) {
                eligiblePeers.add(option);
            } else {
                excludedByCoverage++;
            }
        }

        List<String> metricCodes = PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("start_date", PeerPopulationConstants.ANALYSIS_START.toString());
        parameters.put("periods_per_year", PeerPopulationConstants.PERIODS_PER_YEAR);
        parameters.put("min_downside_observations", PeerPopulationConstants.MIN_DOWNSIDE_OBSERVATIONS);

        Map<String, Integer> calculatedPerMetric = new TreeMap<>();
        for (String code : metricCodes) {
            calculatedPerMetric.put(code, 0);
        }

        Map<Long, PeerFundMetricGenerationSummary> perFund = new TreeMap<>();
        int fundsAttempted = 0;
        int fundsSucceeded = 0;
        int fundsFailed = 0;

        for (SchemeOption peer : eligiblePeers) {
            fundsAttempted++;
            try {
                ProfileCalculationRequest request = new ProfileCalculationRequest(
                    peer.getId(),
                    benchmark.getId(),
                    PeerPopulationConstants.ANALYSIS_END,
                    PeerPopulationConstants.KNOWLEDGE_CUTOFF,
                    PeerPopulationConstants.CALIBRATION_METHODOLOGY_TAG,
                    metricCodes,
                    Map.copyOf(parameters)
                );

                AnalyticalProfileResponse profile = analysisService.executeProfileAnalysis(request);

                Long persistedRunId = profile.context() != null ? profile.context().runId() : null;
                String runStatus = profile.context() != null ? profile.context().runStatus() : "UNKNOWN";

                Map<String, String> metricStatuses = new LinkedHashMap<>();
                int calculated = 0;
                int insufficient = 0;
                int errored = 0;
                for (AnalyticalProfileResponse.ProfileMetricItem item : collectMetricItems(profile)) {
                    metricStatuses.put(item.metricCode(), item.calculationStatus());
                    if ("CALCULATED".equals(item.calculationStatus())) {
                        calculated++;
                        calculatedPerMetric.merge(item.metricCode(), 1, Integer::sum);
                    } else if ("INSUFFICIENT_DATA".equals(item.calculationStatus())) {
                        insufficient++;
                    } else {
                        errored++;
                    }
                }

                if (calculated > 0) {
                    fundsSucceeded++;
                } else {
                    fundsFailed++;
                }

                perFund.put(peer.getId(), new PeerFundMetricGenerationSummary(
                    peer.getId(),
                    peer.getAmfiCode(),
                    amcCode(peer),
                    schemeName(peer),
                    persistedRunId,
                    runStatus,
                    Map.copyOf(metricStatuses),
                    calculated,
                    insufficient,
                    errored,
                    calculated == 0 ? "No canonical metric produced a CALCULATED value for this peer." : null
                ));
            } catch (Exception e) {
                fundsFailed++;
                log.warn("Peer metric generation failed for scheme option {}: {}", peer.getId(), e.getMessage());
                perFund.put(peer.getId(), new PeerFundMetricGenerationSummary(
                    peer.getId(),
                    peer.getAmfiCode(),
                    amcCode(peer),
                    schemeName(peer),
                    null,
                    "FAILED",
                    Map.of(),
                    0,
                    0,
                    metricCodes.size(),
                    "Quant pipeline invocation failure: " + e.getMessage()
                ));
            }
        }

        List<String> messages = new ArrayList<>();
        messages.add(String.format(
            "Canonical peer metric generation completed. RunId=%s. Window=%s..%s. Knowledge cutoff=%s. "
            + "Benchmark=%s(id=%d). Methodology tag=%s. Cohort candidates=%d. "
            + "Eligible peers (>=%d NAV observations, pilot subject fund excluded)=%d. Excluded by coverage=%d. "
            + "Attempted=%d, produced >=1 CALCULATED metric=%d, produced none=%d.",
            runId,
            PeerPopulationConstants.ANALYSIS_START,
            PeerPopulationConstants.ANALYSIS_END,
            PeerPopulationConstants.KNOWLEDGE_CUTOFF,
            benchmark.getCode(),
            benchmark.getId(),
            PeerPopulationConstants.CALIBRATION_METHODOLOGY_TAG,
            candidates.size(),
            PeerPopulationConstants.MIN_OBS_THRESHOLD,
            eligiblePeers.size(),
            excludedByCoverage,
            fundsAttempted,
            fundsSucceeded,
            fundsFailed));

        boolean anyMetricAtThreshold = calculatedPerMetric.values().stream()
            .anyMatch(v -> v >= PeerPopulationConstants.MIN_COHORT_FOR_SIGNIFICANCE);
        if (!anyMetricAtThreshold) {
            messages.add(String.format(
                "No canonical metric reached the >= %d qualifying-peer threshold. EMPIRICALLY READY = FALSE.",
                PeerPopulationConstants.MIN_COHORT_FOR_SIGNIFICANCE));
        }

        return new PeerMetricGenerationReport(
            runId,
            PeerHistoricalNavIngestionService.COHORT_ID,
            PeerPopulationConstants.ANALYSIS_START,
            PeerPopulationConstants.ANALYSIS_END,
            PeerPopulationConstants.KNOWLEDGE_CUTOFF,
            benchmark.getCode(),
            benchmark.getId(),
            metricCodes,
            candidates.size(),
            eligiblePeers.size(),
            excludedByCoverage,
            fundsAttempted,
            fundsSucceeded,
            fundsFailed,
            Map.copyOf(calculatedPerMetric),
            Map.copyOf(perFund),
            messages,
            "Peer MetricResults are produced exclusively by the canonical Python quant engine. "
            + "LOADED != CALCULATED != EMPIRICALLY_READY != VALIDATED != APPROVED. "
            + "Reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is retained and NOT replaced. "
            + "No analytical_score row was created, modified or deleted."
        );
    }

    private static List<AnalyticalProfileResponse.ProfileMetricItem> collectMetricItems(
        AnalyticalProfileResponse profile) {
        List<AnalyticalProfileResponse.ProfileMetricItem> items = new ArrayList<>();
        if (profile == null) {
            return items;
        }
        if (profile.returnMetrics() != null) items.addAll(profile.returnMetrics());
        if (profile.riskMetrics() != null) items.addAll(profile.riskMetrics());
        if (profile.riskAdjustedMetrics() != null) items.addAll(profile.riskAdjustedMetrics());
        if (profile.marketSensitivityMetrics() != null) items.addAll(profile.marketSensitivityMetrics());
        return items;
    }

    // =========================================================================
    // Shared cohort query (identical predicate to PeerPopulationService)
    // =========================================================================

    List<SchemeOption> findCandidateOptions(PeerPopulationCriteria criteria) {
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

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public record PeerFundMetricGenerationSummary(
        Long schemeOptionId,
        String amfiCode,
        String amcCode,
        String schemeName,
        Long calculationRunId,
        String runStatus,
        Map<String, String> metricCalculationStatuses,
        int calculatedMetricCount,
        int insufficientDataMetricCount,
        int errorMetricCount,
        String message
    ) {}

    public record PeerMetricGenerationReport(
        String runId,
        String cohortId,
        LocalDate analysisStartDate,
        LocalDate analysisEndDate,
        OffsetDateTime knowledgeCutoffTime,
        String benchmarkCode,
        Long benchmarkId,
        List<String> canonicalMetricCodes,
        int catalogUniverseCandidatesCount,
        int eligiblePeerCount,
        int excludedByInsufficientNavCount,
        int fundsAttempted,
        int fundsProducedAtLeastOneCalculatedMetric,
        int fundsProducedNoCalculatedMetric,
        Map<String, Integer> calculatedPeerCountByMetric,
        Map<Long, PeerFundMetricGenerationSummary> perFund,
        List<String> messages,
        String governanceNote
    ) {}
}
