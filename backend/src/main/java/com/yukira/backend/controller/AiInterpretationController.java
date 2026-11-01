package com.yukira.backend.controller;

import com.yukira.backend.dto.ai.CanonicalEvidencePacketDto;
import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.MethodologyGovernanceExplainerDto;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.ai.AiInterpretationService;
import com.yukira.backend.service.ai.CanonicalEvidencePacketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Grounded AI Interpretation Layer.
 *
 * Epistemic Contract:
 * - Exposes natural-language interpretations derived strictly from backend DTOs,
 *   analytical profiles, and quantitative calculation runs.
 * - Guarantees schema validation, non-advisory safety checks, and deterministic fallback execution.
 */
@RestController
@RequestMapping("/api/v1")
public class AiInterpretationController {

    private final AiInterpretationService aiInterpretationService;
    private final CalculationRunRepository calculationRunRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final CanonicalEvidencePacketService canonicalEvidencePacketService;

    public AiInterpretationController(
        AiInterpretationService aiInterpretationService,
        CalculationRunRepository calculationRunRepository,
        SchemeOptionRepository schemeOptionRepository,
        @org.springframework.beans.factory.annotation.Autowired(required = false) CanonicalEvidencePacketService canonicalEvidencePacketService
    ) {
        this.aiInterpretationService = aiInterpretationService;
        this.calculationRunRepository = calculationRunRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.canonicalEvidencePacketService = canonicalEvidencePacketService;
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
     * Retrieves grounded AI interpretation for an analytical profile directly.
     * Prevents calculation redundancy and ensures exact synchronization with the profile on screen.
     */
    @PostMapping("/analysis/profile/interpretation")
    public ResponseEntity<?> getInterpretationForProfile(@RequestBody AnalyticalProfileResponse profile) {
        if (profile == null || profile.context() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_PROFILE_PAYLOAD",
                "message", "AnalyticalProfileResponse with context is required."
            ));
        }

        GroundedAiInterpretationDto interpretation = aiInterpretationService.getInterpretationForProfile(profile);
        return ResponseEntity.ok(interpretation);
    }

    /**
     * Evaluates and retrieves grounded AI interpretation for a scheme option under explicit horizon & date context.
     */
    @GetMapping("/analysis/profile/{schemeOptionId}/interpretation")
    public ResponseEntity<?> getProfileInterpretationBySchemeOption(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false, defaultValue = "3Y") String horizon,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime
    ) {
        if (!schemeOptionRepository.existsById(schemeOptionId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "SCHEME_OPTION_NOT_FOUND",
                "message", String.format("SchemeOption #%d does not exist in the canonical registry.", schemeOptionId)
            ));
        }

        try {
            GroundedAiInterpretationDto interpretation = aiInterpretationService.getInterpretationForProfile(
                schemeOptionId, horizon, asOfDate, knowledgeCutoffTime
            );
            return ResponseEntity.ok(interpretation);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_REQUEST",
                "message", ex.getMessage()
            ));
        }
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

    /**
     * Assembles canonical evidence packet standard for a scheme option (Task 196).
     */
    @GetMapping("/ai/evidence-packet/{schemeOptionId}")
    public ResponseEntity<CanonicalEvidencePacketDto> getCanonicalEvidencePacket(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) LocalDate asOfDate,
        @RequestParam(required = false) ZonedDateTime knowledgeCutoff
    ) {
        if (canonicalEvidencePacketService == null) return ResponseEntity.noContent().build();
        CanonicalEvidencePacketDto packet = canonicalEvidencePacketService.assembleEvidencePacket(schemeOptionId, asOfDate, knowledgeCutoff);
        return ResponseEntity.ok(packet);
    }

    /**
     * Retrieves structured quantitative methodology and governance explainers (Task 200).
     */
    @GetMapping("/ai/methodology/explainers")
    public ResponseEntity<List<MethodologyGovernanceExplainerDto>> getMethodologyExplainers() {
        if (canonicalEvidencePacketService == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(canonicalEvidencePacketService.getMethodologyExplainers());
    }
}
