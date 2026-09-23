package com.yukira.backend.controller;

import com.yukira.backend.dto.analysis.Ret02AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret02CalculationRequest;
import com.yukira.backend.dto.analysis.Ret03AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret03CalculationRequest;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        // 1. Parameter Validation
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

        // 2. Scheme Option existence check
        if (!schemeOptionRepository.existsById(request.schemeOptionId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", request.schemeOptionId())
            ));
        }

        // 3. Execution via AnalysisService
        Ret02AnalysisResponse response = analysisService.executeRet02Analysis(request);

        // 4. Explicit error semantics: Do NOT return HTTP 200 with fake values on failure
        if ("INSUFFICIENT_DATA".equals(response.result().calculationStatus()) ||
            "FAILED".equals(response.provenance().runStatus())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves full auditable RET-02 analysis for an existing calculation run.
     */
    @GetMapping("/ret02/{runId}")
    public ResponseEntity<Ret02AnalysisResponse> getRet02Analysis(@PathVariable Long runId) {
        return analysisService.getRet02AnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Alias route for frontend /analysis/[id] direct retrieval.
     */
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves full auditable RET-03 analysis for an existing calculation run.
     */
    @GetMapping("/ret03/{runId}")
    public ResponseEntity<Ret03AnalysisResponse> getRet03Analysis(@PathVariable Long runId) {
        return analysisService.getRet03AnalysisByRunId(runId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
