package com.yukira.backend.controller;

import com.yukira.backend.dto.analysis.*;
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
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
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rsk07/{runId}")
    public ResponseEntity<RiskAnalysisResponse> getRsk07Analysis(@PathVariable Long runId) {
        return analysisService.getRiskAnalysisByRunIdAndCode(runId, "RSK-07")
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
