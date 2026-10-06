package com.yukira.backend.controller;

import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.ai.AiInterpretationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Grounded AI Interpretation Layer.
 *
 * Epistemic Contract:
 * - Exposes natural-language interpretations derived strictly from backend DTOs and quantitative calculation runs.
 * - Guarantees schema validation, non-advisory safety checks, and deterministic fallback execution.
 */
@RestController
@RequestMapping("/api/v1")
public class AiInterpretationController {

    private final AiInterpretationService aiInterpretationService;
    private final CalculationRunRepository calculationRunRepository;
    private final SchemeOptionRepository schemeOptionRepository;

    public AiInterpretationController(
        AiInterpretationService aiInterpretationService,
        CalculationRunRepository calculationRunRepository,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this.aiInterpretationService = aiInterpretationService;
        this.calculationRunRepository = calculationRunRepository;
        this.schemeOptionRepository = schemeOptionRepository;
    }

    /**
     * Retrieves grounded AI interpretation for a specific calculation run ID.
     */
    @GetMapping("/analysis/{runId}/interpretation")
    public ResponseEntity<?> getAnalysisInterpretation(@PathVariable Long runId) {
        if (!calculationRunRepository.existsById(runId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CALCULATION_RUN_NOT_FOUND",
                "message", String.format("CalculationRun #%d does not exist in the canonical registry.", runId)
            ));
        }

        GroundedAiInterpretationDto interpretation = aiInterpretationService.getInterpretationForRun(runId);
        return ResponseEntity.ok(interpretation);
    }

    /**
     * Retrieves grounded AI interpretation for a scheme option.
     */
    @GetMapping("/scores/{schemeOptionId}/interpretation")
    public ResponseEntity<?> getScoreInterpretation(@PathVariable Long schemeOptionId) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        GroundedAiInterpretationDto interpretation = aiInterpretationService.getInterpretationForSchemeOption(schemeOptionId);
        return ResponseEntity.ok(interpretation);
    }

    /**
     * Retrieves grounded AI interpretation for the authenticated investor's portfolio.
     */
    @GetMapping("/portfolio/interpretation")
    public ResponseEntity<?> getPortfolioInterpretation(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        GroundedAiInterpretationDto interpretation = aiInterpretationService.getInterpretationForPortfolio(subject);
        return ResponseEntity.ok(interpretation);
    }
}
