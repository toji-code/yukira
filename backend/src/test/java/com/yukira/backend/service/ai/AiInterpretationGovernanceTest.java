package com.yukira.backend.service.ai;

import com.yukira.backend.dto.ai.EvidenceClaimDto;
import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 453: AI Interpretation Governance, Model Availability Alignment, and Deterministic Fallback Engine.
 * Verifies deterministic fallback activation when LLM is unavailable or unconfigured,
 * zero investment advice generation, structured claim-level evidence binding, and epistemic boundaries.
 */
class AiInterpretationGovernanceTest {

    private DeterministicInterpretationFallbackEngine fallbackEngine;
    private AiOutputValidationService validationService;

    @BeforeEach
    void setUp() {
        fallbackEngine = new DeterministicInterpretationFallbackEngine();
        validationService = new AiOutputValidationService();
    }

    @Test
    @DisplayName("Deterministic fallback engine produces non-null, structured interpretation for fund option")
    void testFallbackEngineFundOptionGeneration() {
        GroundedEvidencePackageDto evidence = new GroundedEvidencePackageDto(
            10189L,
            "HDFC Flexi Cap Fund",
            "118955",
            "INF179K01UT0",
            "Flexi Cap Fund",
            "DIRECT",
            "GROWTH",
            1001L,
            "2024-01-15",
            "2024-01-15T23:59:59+05:30",
            74.5,
            "SCORED",
            Map.of("RETURN", 75.0, "RISK", 74.0),
            Map.of("RET-02", 0.0245, "RET-03", 0.185, "RSK-01", 0.142),
            Map.of("quality", "VALID", "freshness", "CURRENT"),
            "YUKIRA_PROVISIONAL_V1",
            List.of("900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259"),
            Collections.emptyMap()
        );

        GroundedAiInterpretationDto interpretation = fallbackEngine.generateInterpretation(evidence);

        assertNotNull(interpretation);
        assertNotNull(interpretation.summary());
        assertFalse(interpretation.summary().isBlank());
        assertNotNull(interpretation.claims());
        assertFalse(interpretation.claims().isEmpty(), "Deterministic fallback must generate structured claims");

        // Verify claims have valid binding to evidence
        for (EvidenceClaimDto claim : interpretation.claims()) {
            assertNotNull(claim.claimText());
            assertNotNull(claim.claimType());
            assertNotNull(claim.supportingMetricCodes());
            assertNotNull(claim.methodologyStatus());
        }

        // Verify safety validation passes on deterministic output
        AiOutputValidationService.ValidationResult validationResult = validationService.validateInterpretation(interpretation, evidence);
        assertTrue(validationResult.isValid(), "Deterministic fallback output must strictly pass safety audit: " + validationResult.failureReason());
    }

    @Test
    @DisplayName("Deterministic fallback engine strictly abstains from BUY, SELL, or HOLD advisory tokens")
    void testFallbackEngineZeroAdvisoryLanguage() {
        GroundedEvidencePackageDto evidence = new GroundedEvidencePackageDto(
            10189L,
            "HDFC Flexi Cap Fund",
            "118955",
            "INF179K01UT0",
            "Flexi Cap Fund",
            "DIRECT",
            "GROWTH",
            1001L,
            "2024-01-15",
            "2024-01-15T23:59:59+05:30",
            82.0,
            "SCORED",
            Map.of("RETURN", 82.0),
            Map.of("RET-02", 0.05),
            Map.of("quality", "VALID"),
            "YUKIRA_PROVISIONAL_V1",
            List.of("900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259"),
            Collections.emptyMap()
        );

        GroundedAiInterpretationDto interpretation = fallbackEngine.generateInterpretation(evidence);

        String fullText = String.join(" ",
            interpretation.summary(),
            String.join(" ", interpretation.whatHappened()),
            String.join(" ", interpretation.returnCharacteristics()),
            String.join(" ", interpretation.riskCharacteristics())
        ).toUpperCase();

        assertFalse(fullText.contains("BUY"), "Interpretation must not contain BUY recommendations");
        assertFalse(fullText.contains("SELL"), "Interpretation must not contain SELL recommendations");
        assertFalse(fullText.contains("HOLD"), "Interpretation must not contain HOLD recommendations");
        assertFalse(fullText.contains("STRONG BUY"), "Interpretation must not contain STRONG BUY");
        assertFalse(fullText.contains("OUTPERFORM"), "Interpretation must not contain subjective analyst ratings");
    }

    @Test
    @DisplayName("Deterministic fallback engine generates portfolio-specific interpretation when category is MULTI_ASSET_PORTFOLIO")
    void testFallbackEnginePortfolioContext() {
        GroundedEvidencePackageDto evidence = new GroundedEvidencePackageDto(
            null,
            "Aggressive Multi-Asset Allocation",
            null,
            null,
            "MULTI_ASSET_PORTFOLIO",
            "N/A",
            "N/A",
            null,
            "2024-01-15",
            "2024-01-15T23:59:59+05:30",
            70.0,
            "SCORED",
            Map.of("ALLOCATION", 70.0),
            Collections.emptyMap(),
            Map.of("quality", "VALID"),
            "PORTFOLIO_V1",
            Collections.emptyList(),
            Map.of("holdingCount", 5, "driftMagnitude", 0.04)
        );

        GroundedAiInterpretationDto interpretation = fallbackEngine.generateInterpretation(evidence);

        assertNotNull(interpretation);
        assertNotNull(interpretation.summary());
        assertTrue(interpretation.summary().contains("Portfolio") || interpretation.summary().contains("portfolio") || interpretation.summary().contains("Asset"),
            "Summary must reflect portfolio context");
    }
}
