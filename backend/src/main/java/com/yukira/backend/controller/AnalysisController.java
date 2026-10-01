package com.yukira.backend.controller;

import com.yukira.backend.dto.analysis.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.yukira.backend.dto.analysis.ComparisonRequest;
import com.yukira.backend.dto.analysis.ComparisonResponse;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final SchemeOptionRepository schemeOptionRepository;

    public AnalysisController(AnalysisService analysisService, SchemeOptionRepository schemeOptionRepository) {
        this.analysisService = analysisService;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    /**
     * Executes real RET-02 Simple Period Return vertical slice with explicit parameters.
     * Rejects arbitrary source URLs and does not accept client-injected financial returns.
     */
    @PostMapping("/ret02")
    public ResponseEntity<?> executeRet02(@RequestBody Ret02CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "Canonical schemeOptionId is required."
            ));
        }
        if (request.startDate() == null || request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_DATES",
                "message", "Both startDate and endDate are required."
            ));
        }
        if (!request.startDate().isBefore(request.endDate())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PERIOD_SEQUENCE",
                "message", String.format("Requested startDate %s must be strictly before endDate %s.",
                    request.startDate(), request.endDate())
            ));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_CUTOFF",
                "message", "knowledgeCutoffTime is required to enforce point-in-time constraints."
            ));
        }

        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        Ret02AnalysisResponse response = analysisService.executeRet02Analysis(request);

        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) ||
            "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ret02/{runId}")
    public ResponseEntity<Ret02AnalysisResponse> getRet02Analysis(@PathVariable Long runId) {
        return analysisService.getRet02AnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{runId}")
    public ResponseEntity<?> getAnalysisByRunId(@PathVariable Long runId) {
        return analysisService.getAnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RET-03 (3Y CAGR) vertical slice.
     */
    @PostMapping("/ret03")
    public ResponseEntity<?> executeRet03(@RequestBody Ret03CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "Canonical schemeOptionId is required."
            ));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_DATES",
                "message", "endDate is required for RET-03 3Y CAGR calculation."
            ));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_CUTOFF",
                "message", "knowledgeCutoffTime is required to enforce point-in-time constraints."
            ));
        }

        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        Ret03AnalysisResponse response = analysisService.executeRet03Analysis(request);

        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) ||
            "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ret03/{runId}")
    public ResponseEntity<Ret03AnalysisResponse> getRet03Analysis(@PathVariable Long runId) {
        return analysisService.getRet03AnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-01 (3-Year Annualized Volatility) vertical slice.
     */
    @PostMapping("/rsk01")
    public ResponseEntity<?> executeRsk01(@RequestBody Rsk01CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "Canonical schemeOptionId is required."
            ));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_DATES",
                "message", "endDate is required for RSK-01 3Y Volatility calculation."
            ));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_CUTOFF",
                "message", "knowledgeCutoffTime is required to enforce point-in-time constraints."
            ));
        }

        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        Rsk01AnalysisResponse response = analysisService.executeRsk01Analysis(request);

        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) ||
            "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk01/{runId}")
    public ResponseEntity<Rsk01AnalysisResponse> getRsk01Analysis(@PathVariable Long runId) {
        return analysisService.getRsk01AnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-02 (Downside Semideviation, 3Y) vertical slice.
     */
    @PostMapping("/rsk02")
    public ResponseEntity<?> executeRsk02(@RequestBody Rsk02CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-02 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk02Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk02/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk02Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-02")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-03 (3-Year Maximum Drawdown) vertical slice.
     */
    @PostMapping("/rsk03")
    public ResponseEntity<?> executeRsk03(@RequestBody Rsk03CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-03 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk03Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk03/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk03Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-03")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-04 (Maximum Drawdown Duration) vertical slice.
     */
    @PostMapping("/rsk04")
    public ResponseEntity<?> executeRsk04(@RequestBody Rsk04CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-04 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk04Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk04/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk04Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-04")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-05 (Ulcer Index, 3Y) vertical slice.
     */
    @PostMapping("/rsk05")
    public ResponseEntity<?> executeRsk05(@RequestBody Rsk05CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-05 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk05Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk05/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk05Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-05")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-06 (Historical VaR 95%, 3Y) vertical slice.
     */
    @PostMapping("/rsk06")
    public ResponseEntity<?> executeRsk06(@RequestBody Rsk06CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-06 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk06Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk06/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk06Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-06")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes real RSK-07 (Historical Expected Shortfall / CVaR 95%, 3Y) vertical slice.
     */
    @PostMapping("/rsk07")
    public ResponseEntity<?> executeRsk07(@RequestBody Rsk07CalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PARAMETER", "message", "Canonical schemeOptionId is required."));
        }
        if (request.endDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_DATES", "message", "endDate is required for RSK-07 calculation."));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_PIT_CUTOFF", "message", "knowledgeCutoffTime is required."));
        }
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SCHEME_OPTION_NOT_FOUND", "message", "SchemeOption not found."));
        }

        RiskAnalysisResponse response = analysisService.executeRsk07Analysis(request);
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) || "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.valueOf(422)).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk07/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk07Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-07")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Executes unified 3-Year Institutional Risk-Return Profile (16 metrics) under CalculationRun architecture.
     */
    @PostMapping({"/profile", "/composite"})
    public ResponseEntity<?> executeProfile(@RequestBody ProfileCalculationRequest request) {
        if (request.schemeOptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "Canonical schemeOptionId is required."
            ));
        }
        if (request.asOfDate() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_DATES",
                "message", "asOfDate is required for profile calculation."
            ));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_CUTOFF",
                "message", "knowledgeCutoffTime is required to enforce point-in-time constraints."
            ));
        }

        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        AnalyticalProfileResponse response = analysisService.executeProfileAnalysis(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compare")
    public ResponseEntity<?> executeComparison(@RequestBody ComparisonRequest request) {
        if (request.schemeOptionIds() == null || request.schemeOptionIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PARAMETER",
                "message", "At least two schemeOptionIds are required for comparison."
            ));
        }
        if (request.schemeOptionIds().size() < 2) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INSUFFICIENT_SCHEMES",
                "message", "Minimum 2 schemes required for comparison."
            ));
        }
        if (request.schemeOptionIds().size() > 4) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "TOO_MANY_SCHEMES",
                "message", "Maximum 4 schemes allowed for comparison."
            ));
        }
        if (request.asOfDate() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_DATES",
                "message", "asOfDate is required for comparison."
            ));
        }
        if (request.knowledgeCutoffTime() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_CUTOFF",
                "message", "knowledgeCutoffTime is required to enforce point-in-time constraints."
            ));
        }

        for (Long id : request.schemeOptionIds()) {
            if (!schemeOptionRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "SCHEME_OPTION_NOT_FOUND",
                    "message", String.format("SchemeOption #%d does not exist in the canonical registry.", id)
                ));
            }
        }

        ComparisonResponse response = analysisService.executeComparison(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Executes real 6-dimensional Data Quality & Anomaly Center audit for a fund.
     */
    @GetMapping("/quality/{schemeOptionId}")
    public ResponseEntity<?> getDataQualityAudit(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String knowledgeCutoffTime
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        java.time.OffsetDateTime cutoff = knowledgeCutoffTime != null
            ? java.time.OffsetDateTime.parse(knowledgeCutoffTime)
            : java.time.OffsetDateTime.now();

        DataQualityAuditResponse response = analysisService.executeDataQualityAudit(schemeOptionId, cutoff);
        return ResponseEntity.ok(response);
    }

    /**
     * Executes real Rolling Return and Outperformance Consistency Analysis (RET-05 / RET-06).
     * Deterministic Julian 365.25 annualization, synchronous pairing vs NIFTY 500 TRI, strict PIT enforcement.
     */
    @GetMapping("/rolling/{schemeOptionId}")
    public ResponseEntity<?> getRollingConsistency(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        java.time.LocalDate date = asOfDate != null
            ? java.time.LocalDate.parse(asOfDate)
            : java.time.LocalDate.of(2024, 1, 15);

        java.time.OffsetDateTime cutoff = knowledgeCutoffTime != null
            ? java.time.OffsetDateTime.parse(knowledgeCutoffTime)
            : java.time.OffsetDateTime.now();

        RollingConsistencyResponse response = analysisService.executeRollingConsistencyAnalysis(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Executes real Capture Ratios & Asymmetry Analysis (MKT-03 / MKT-04 / MKT-05).
     * Synchronously aligned pairing vs NIFTY 500 TRI, strict PIT enforcement.
     */
    @GetMapping("/capture/{schemeOptionId}")
    public ResponseEntity<?> getCaptureRatios(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        java.time.LocalDate date = asOfDate != null
            ? java.time.LocalDate.parse(asOfDate)
            : java.time.LocalDate.of(2024, 1, 15);

        java.time.OffsetDateTime cutoff = knowledgeCutoffTime != null
            ? java.time.OffsetDateTime.parse(knowledgeCutoffTime)
            : java.time.OffsetDateTime.now();

        CaptureRatioResponse response = analysisService.executeCaptureRatioAnalysis(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Executes real Market-Relative Tracking Consistency & Information Ratio Analysis (Â§MKT-01 / Â§MKT-02).
     * Synchronously aligned pairing vs NIFTY 500 TRI, strict PIT enforcement, minimum N >= 700 threshold.
     */
    @GetMapping({"/tracking-consistency/{schemeOptionId}", "/tracking/{schemeOptionId}"})
    public ResponseEntity<?> getTrackingConsistency(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        java.time.LocalDate date = asOfDate != null
            ? java.time.LocalDate.parse(asOfDate)
            : java.time.LocalDate.of(2024, 1, 15);

        java.time.OffsetDateTime cutoff = knowledgeCutoffTime != null
            ? java.time.OffsetDateTime.parse(knowledgeCutoffTime)
            : java.time.OffsetDateTime.now();

        TrackingConsistencyResponse response = analysisService.executeTrackingConsistencyAnalysis(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Executes Benchmark Relationship & Explanatory Power Analysis
     * (REL-02 Benchmark Correlation / REL-03 R-Squared).
     */
    @GetMapping({"/benchmark-relationship/{schemeOptionId}", "/relationship/{schemeOptionId}"})
    public ResponseEntity<?> getBenchmarkRelationship(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        if (asOfDate == null || knowledgeCutoffTime == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_PARAMETERS",
                "message", "asOfDate and knowledgeCutoffTime are required for point-in-time benchmark relationship analysis."
            ));
        }

        java.time.LocalDate date;
        java.time.OffsetDateTime cutoff;
        try {
            date = java.time.LocalDate.parse(asOfDate);
            cutoff = java.time.OffsetDateTime.parse(knowledgeCutoffTime);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_OR_CUTOFF",
                "message", "asOfDate must be ISO yyyy-MM-dd and knowledgeCutoffTime must be an ISO offset timestamp."
            ));
        }

        if (date.isAfter(cutoff.toLocalDate())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PIT_SEQUENCE",
                "message", "asOfDate must not be after knowledgeCutoffTime."
            ));
        }

        if (benchmarkId != null && !analysisService.benchmarkExists(benchmarkId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "BENCHMARK_NOT_FOUND",
                "message", String.format("Benchmark #%d does not exist in the canonical registry.", benchmarkId)
            ));
        }

        BenchmarkRelationshipResponse response = analysisService.executeBenchmarkRelationshipAnalysis(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }
    /**
     * Aggregates existing benchmark-relative outputs for the Fund Profile Benchmark Relationship panel.
     */
    @GetMapping({"/benchmark-relationship-panel/{schemeOptionId}", "/relationship-panel/{schemeOptionId}"})
    public ResponseEntity<?> getBenchmarkRelationshipPanel(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        if (asOfDate == null || knowledgeCutoffTime == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_PARAMETERS",
                "message", "asOfDate and knowledgeCutoffTime are required for point-in-time benchmark relationship panel analysis."
            ));
        }

        java.time.LocalDate date;
        java.time.OffsetDateTime cutoff;
        try {
            date = java.time.LocalDate.parse(asOfDate);
            cutoff = java.time.OffsetDateTime.parse(knowledgeCutoffTime);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_OR_CUTOFF",
                "message", "asOfDate must be ISO yyyy-MM-dd and knowledgeCutoffTime must be an ISO offset timestamp."
            ));
        }

        if (date.isAfter(cutoff.toLocalDate())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PIT_SEQUENCE",
                "message", "asOfDate must not be after knowledgeCutoffTime."
            ));
        }

        if (benchmarkId != null && !analysisService.benchmarkExists(benchmarkId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "BENCHMARK_NOT_FOUND",
                "message", String.format("Benchmark #%d does not exist in the canonical registry.", benchmarkId)
            ));
        }

        BenchmarkRelationshipPanelResponse response = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }
    /**
     * Executes Benchmark Beta Dynamics & Systematic Covariance Analysis
     * (Â§REL-01 Standard Beta / Â§REL-04 Downside Beta / Â§REL-05 Upside Beta).
     * Synchronously aligned pairing vs NIFTY 500 TRI, strict PIT enforcement.
     */
    @GetMapping({"/beta/{schemeOptionId}", "/beta-dynamics/{schemeOptionId}"})
    public ResponseEntity<?> getBetaDynamics(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime,
        @RequestParam(required = false) Long benchmarkId
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        if (asOfDate == null || knowledgeCutoffTime == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_PIT_PARAMETERS",
                "message", "asOfDate and knowledgeCutoffTime are required for point-in-time beta analysis."
            ));
        }

        java.time.LocalDate date;
        java.time.OffsetDateTime cutoff;
        try {
            date = java.time.LocalDate.parse(asOfDate);
            cutoff = java.time.OffsetDateTime.parse(knowledgeCutoffTime);
        } catch (java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_OR_CUTOFF",
                "message", "asOfDate must be ISO yyyy-MM-dd and knowledgeCutoffTime must be an ISO offset timestamp."
            ));
        }

        if (date.isAfter(cutoff.toLocalDate())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PIT_SEQUENCE",
                "message", "asOfDate must not be after knowledgeCutoffTime."
            ));
        }

        if (benchmarkId != null && !analysisService.benchmarkExists(benchmarkId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "BENCHMARK_NOT_FOUND",
                "message", String.format("Benchmark #%d does not exist in the canonical registry.", benchmarkId)
            ));
        }

        BetaDynamicsResponse response = analysisService.executeBetaCalculation(
            schemeOptionId, date, cutoff, benchmarkId
        );
        return ResponseEntity.ok(response);
    }
}
