package com.yukira.backend.controller;

import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoringController {

    private static final Logger log = LoggerFactory.getLogger(ScoringController.class);

    private final AnalyticalScoringService scoringService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final ScoreMethodologyConfig methodologyConfig;
    private final com.yukira.backend.scoring.population.PeerPopulationService peerPopulationService;
    private final com.yukira.backend.scoring.population.PeerMetricCalculationService peerMetricCalculationService;
    private final com.yukira.backend.service.scoring.AnalyticalRefreshService analyticalRefreshService;

    public ScoringController(
        AnalyticalScoringService scoringService,
        SchemeOptionRepository schemeOptionRepository,
        ScoreMethodologyConfig methodologyConfig
    ) {
        this(scoringService, schemeOptionRepository, methodologyConfig, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScoringController(
        AnalyticalScoringService scoringService,
        SchemeOptionRepository schemeOptionRepository,
        ScoreMethodologyConfig methodologyConfig,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.scoring.population.PeerPopulationService peerPopulationService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.scoring.population.PeerMetricCalculationService peerMetricCalculationService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.service.scoring.AnalyticalRefreshService analyticalRefreshService
    ) {
        this.scoringService = scoringService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.methodologyConfig = methodologyConfig;
        this.peerPopulationService = peerPopulationService;
        this.peerMetricCalculationService = peerMetricCalculationService;
        this.analyticalRefreshService = analyticalRefreshService;
    }

    /**
     * Executes deterministic analytical score calculation for a scheme option.
     */
    @PostMapping("/calculate")
    public ResponseEntity<?> calculateScore(@RequestBody ScoreCalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "schemeOptionId is required."
            ));
        }

        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        AnalyticalScoreResponse response = scoringService.calculateScore(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the latest analytical score for a scheme option.
     */
    @GetMapping("/{schemeOptionId}/latest")
    public ResponseEntity<?> getLatestScore(@PathVariable Long schemeOptionId) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        return scoringService.getLatestScore(schemeOptionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    /**
     * Retrieves all historical analytical score snapshots for a scheme option.
     */
    @GetMapping("/{schemeOptionId}/history")
    public ResponseEntity<?> getScoreHistory(@PathVariable Long schemeOptionId) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        return ResponseEntity.ok(scoringService.getScoreHistory(schemeOptionId));
    }

    /**
     * Retrieves the analytical score associated with a specific calculation run.
     */
    @GetMapping("/run/{calculationRunId}")
    public ResponseEntity<?> getScoreByCalculationRunId(@PathVariable Long calculationRunId) {
        return scoringService.getScoreByCalculationRunId(calculationRunId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Exposes machine-readable methodology configuration (weights, reference population, status, governance).
     */
    @GetMapping("/methodology")
    public ResponseEntity<?> getMethodologyConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scoreVersion", methodologyConfig.getScoreVersion());
        response.put("methodologyStatus", methodologyConfig.getMethodologyStatus());
        response.put("referencePopulation", methodologyConfig.getReferencePopulationCode());
        response.put("effectiveDate", methodologyConfig.getEffectiveDate());
        response.put("minRequiredObservations", methodologyConfig.getMinRequiredObservations());
        response.put("performanceDimensions", methodologyConfig.getDimensions());
        response.put("scoredDimensionCodes", ScoreMethodologyConfig.SCORED_DIMENSION_CODES);
        response.put("authorizedScoreInputMetrics", ScoreMethodologyConfig.SCORE_INPUT_METRIC_CODES);
        response.put("analyticalNonScoreMetrics", ScoreMethodologyConfig.ANALYTICAL_NON_SCORE_METRIC_CODES);
        response.put("evidenceConfidenceWeight", BigDecimal.ZERO);
        response.put("evidenceConfidenceRole", "Epistemic data verification; strictly zero weight in numerical analytical score. Weak evidence never acts as a performance penalty: high score + low confidence and low score + high confidence are fully supported.");
        response.put("mkt05CalibrationStatus", "UNCALIBRATED / NOT ELIGIBLE FOR SCORE");
        response.put("unauthorizedMetrics", ScoreMethodologyConfig.UNAUTHORIZED_METRICS);
        response.put("disclaimer", "YUKIRA_SCORE_V1 is a candidate research methodology. Reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is PROVISIONAL, not empirical full-universe percentiles, and not peer-ranked against the complete universe. IMPLEMENTED != VALIDATED != APPROVED.");
        return ResponseEntity.ok(response);
    }

    /**
     * Generates canonical peer MetricResults for empirical reference-population calibration.
     *
     * POST /api/v1/scores/peer-metrics/run
     *
     * - Delegates every peer to the canonical calculation pipeline
     *   (CalculationOrchestratorService -> Python quant engine) under the frozen
     *   calibration configuration: window 2021-01-01 -> 2024-01-15,
     *   knowledge cutoff 2024-01-31T23:59:59.999+05:30, benchmark NIFTY_500_TRI,
     *   methodology tag APPROVED_M2N.
     * - Requests ONLY the canonical YUKIRA_SCORE_V1 input metrics
     *   (RET-03, RET-07, RSK-01, RSK-02, RSK-03, REL-02, RAT-04, MKT-01, MKT-05, MKT-02).
     *   RET-02 is NOT a score input and is never requested.
     * - MKT-06, REL-04, REL-05, REL-06 and RAT-05 are rejected by metric-identity guard.
     * - Does NOT touch analytical_score and does NOT replace
     *   INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1.
     *
     * WARNING: long-running batch (one calculation run per eligible peer).
     */
    @PostMapping("/peer-metrics/run")
    public ResponseEntity<?> generatePeerMetrics() {
        if (peerMetricCalculationService == null) {
            return ResponseEntity.ok(Map.of(
                "error", "PEER_METRIC_CALCULATION_SERVICE_UNAVAILABLE",
                "message", "PeerMetricCalculationService is not registered."
            ));
        }
        try {
            return ResponseEntity.ok(peerMetricCalculationService.generatePeerMetrics(
                com.yukira.backend.scoring.population.PeerPopulationCriteria.defaultFlexiCap()));
        } catch (Exception e) {
            // e.getMessage() may be null; Map.of() would then throw and mask the root cause.
            log.error("Peer metric generation failed", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "PEER_METRIC_GENERATION_FAILED");
            error.put("exception", e.getClass().getName());
            error.put("message", e.getMessage() != null ? e.getMessage() : "NO_MESSAGE_PROVIDED");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Evaluates empirical peer reference population readiness from persisted evidence.
     *
     * GET /api/v1/scores/reference-population
     *
     * - Reports exact observation counts and window boundaries per fund (LOADED / ELIGIBLE).
     * - Reports the full readiness ladder per canonical metric: LOADED, ELIGIBLE, CALCULATED,
     *   EMPIRICALLY_READY, VALIDATED, APPROVED.
     * - Reports empirical distributions (N, min, P05, P25, median, P75, P95, max, mean, sample
     *   stdev) built exclusively from CALCULATED canonical MetricResults of eligible peers,
     *   plus explicit missing / excluded accounting.
     * - Reports verified NIFTY_500_TRI and FBIL_91D_TBILL coverage and any window gaps.
     * - Reports the provisional-versus-empirical evidence diff.
     *   INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is RETAINED, never replaced.
     */
    @GetMapping("/reference-population")
    public ResponseEntity<?> getReferencePopulationEvaluation() {
        if (peerPopulationService == null) {
            return ResponseEntity.ok(Map.of(
                "activeProvisionalPopulation", ScoreMethodologyConfig.REFERENCE_POPULATION_CODE,
                "status", "PROVISIONAL_INSUFFICIENT_DATA",
                "empiricallyReady", false,
                "validated", false,
                "approved", false,
                "disclaimer", "Reference population remains strictly provisional. PeerPopulationService not available."
            ));
        }
        return ResponseEntity.ok(peerPopulationService.generateFullCohortReport(
            com.yukira.backend.scoring.population.PeerPopulationCriteria.defaultFlexiCap()
        ));
    }

    /**
     * Triggers bulk scoring calculation across all active mutual funds in the database universe.
     * Idempotent and deterministic.
     */
    @PostMapping("/universe/run")
    public ResponseEntity<?> runUniverseScoring(
        @RequestParam(required = false) String asOfDateStr
    ) {
        java.time.LocalDate asOfDate = asOfDateStr != null
            ? java.time.LocalDate.parse(asOfDateStr)
            : java.time.LocalDate.of(2024, 1, 15);
        java.time.OffsetDateTime knowledgeCutoff = java.time.OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        com.yukira.backend.scoring.dto.UniverseScoringReport report = scoringService.scoreActiveUniverse(asOfDate, knowledgeCutoff);
        return ResponseEntity.ok(report);
    }

    /**
     * Executes controlled production analytical refresh batch across the active fund universe.
     * Authenticated / internal administrative endpoint.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAnalyticalScores(
        @RequestParam(required = false) String asOfDateStr,
        @RequestParam(required = false) String knowledgeCutoffStr
    ) {
        if (analyticalRefreshService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "ANALYTICAL_REFRESH_SERVICE_UNAVAILABLE",
                "message", "AnalyticalRefreshService is not configured."
            ));
        }

        java.time.LocalDate asOfDate = asOfDateStr != null && !asOfDateStr.isBlank()
            ? java.time.LocalDate.parse(asOfDateStr)
            : null;

        java.time.OffsetDateTime knowledgeCutoff = knowledgeCutoffStr != null && !knowledgeCutoffStr.isBlank()
            ? java.time.OffsetDateTime.parse(knowledgeCutoffStr)
            : null;

        com.yukira.backend.dto.scoring.AnalyticalRefreshResultDto result =
            analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        return ResponseEntity.ok(result);
    }

    /**
     * Exposes current score read model for a scheme option with data freshness & PIT context.
     * Pure read operation — zero score calculation on investor HTTP request.
     */
    @GetMapping("/{schemeOptionId}/current")
    public ResponseEntity<?> getCurrentScore(@PathVariable Long schemeOptionId) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        if (analyticalRefreshService == null) {
            return scoringService.getLatestScore(schemeOptionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }

        return analyticalRefreshService.getCurrentScore(schemeOptionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
