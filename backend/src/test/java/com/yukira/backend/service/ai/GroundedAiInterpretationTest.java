package com.yukira.backend.service.ai;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroundedAiInterpretationTest {

    @Mock
    private CalculationRunRepository calculationRunRepository;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private AnalyticalScoringService scoringService;

    private EvidenceGrounderService evidenceGrounder;
    private DeterministicInterpretationFallbackEngine fallbackEngine;
    private AiOutputValidationService validationService;
    private AiInterpretationService aiInterpretationService;

    @BeforeEach
    void setUp() {
        evidenceGrounder = new EvidenceGrounderService(
            calculationRunRepository,
            schemeOptionRepository,
            analysisService,
            scoringService,
            null
        );
        fallbackEngine = new DeterministicInterpretationFallbackEngine();
        validationService = new AiOutputValidationService();
        aiInterpretationService = new AiInterpretationService(
            evidenceGrounder,
            fallbackEngine,
            validationService
        );
    }

    @Test
    void testEvidenceGroundingPackageConstruction() {
        SchemeOption option = createMockSchemeOption(10189L, "HDFC Flexi Cap Fund", "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));

        AnalyticalScoreResponse scoreResponse = new AnalyticalScoreResponse(
            99L, 10189L, "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            BigDecimal.valueOf(74.5), BigDecimal.valueOf(0.9), "SCORED",
            "YUKIRA_SCORE_V1", "PROVISIONAL_V1", LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), 7851L, "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
            "Valid score", "Disclaimer", Collections.emptyList(), null
        );
        when(scoringService.getLatestScore(10189L)).thenReturn(Optional.of(scoreResponse));

        GroundedEvidencePackageDto pkg = evidenceGrounder.buildEvidencePackageForSchemeOption(10189L);

        assertNotNull(pkg);
        assertEquals(10189L, pkg.schemeOptionId());
        assertEquals("HDFC Flexi Cap Fund", pkg.schemeName());
        assertEquals("118955", pkg.amfiCode());
        assertEquals(74.5, pkg.overallScore());
        assertEquals("SCORED", pkg.scoreStatus());
    }

    @Test
    void testSafetyFilterRejectsBuySellHoldRecommendations() {
        GroundedAiInterpretationDto unsafeDto = new GroundedAiInterpretationDto(
            "You should buy this fund immediately.",
            List.of("The fund returned 15%."),
            List.of("We give a strong buy recommendation with guaranteed returns."),
            List.of("Low risk."),
            List.of("None."),
            List.of("None."),
            List.of("What to buy?"),
            Map.of(), 100L, 10189L, "V1", "2024-01-15", "2024-01-31",
            List.of(), "VALIDATED_GROUNDED_AI", false, "test-model", "2026-10-06"
        );

        AiOutputValidationService.ValidationResult result = validationService.validateInterpretation(unsafeDto);
        assertFalse(result.isValid());
        assertTrue(result.failureReason().contains("buy"));
    }

    @Test
    void testSafetyFilterApprovesGroundedNonAdvisoryProse() {
        GroundedAiInterpretationDto safeDto = new GroundedAiInterpretationDto(
            "Deterministic analysis of HDFC Flexi Cap Fund.",
            List.of("3Y CAGR was 15.2%."),
            List.of("Fund Sharpe ratio of 1.15 reflects above-median risk-adjusted efficiency."),
            List.of("Volatility remains exposed to market drawdowns."),
            List.of("Candidate methodology status applies."),
            List.of("Regime shifts in interest rates."),
            List.of("Has the manager process changed?"),
            Map.of(), 100L, 10189L, "V1", "2024-01-15", "2024-01-31",
            List.of(), "VALIDATED_GROUNDED_AI", false, "test-model", "2026-10-06"
        );

        AiOutputValidationService.ValidationResult result = validationService.validateInterpretation(safeDto);
        assertTrue(result.isValid());
    }

    @Test
    void testDeterministicFallbackEngineFulfillsSixQuestionFramework() {
        GroundedEvidencePackageDto evidence = new GroundedEvidencePackageDto(
            10189L, "HDFC Flexi Cap Fund", "118955", "INF179K01UT0", "Equity", "DIRECT", "GROWTH",
            7851L, "2024-01-15", "2024-01-31T23:59:59+05:30", 72.4, "SCORED",
            Map.of("Returns", 75.0, "Volatility", 68.0, "Downside Risk", 74.0),
            Map.of("RET-03", Map.of("code", "RET-03", "name", "3Y CAGR", "formatted", "+18.4%")),
            Map.of("quality", "VALID", "freshness", "CURRENT"),
            "CANDIDATE_V1", List.of("digest123"), null
        );

        GroundedAiInterpretationDto fallback = fallbackEngine.generateInterpretation(evidence);

        assertNotNull(fallback);
        assertTrue(fallback.isFallback());
        assertEquals("DETERMINISTIC_FALLBACK", fallback.epistemicStatus());

        // Verify all 6 question outputs are present
        assertFalse(fallback.whatHappened().isEmpty(), "Question 1: What happened?");
        assertFalse(fallback.interpretation().isEmpty(), "Question 2: What does it mean?");
        assertFalse(fallback.riskFactors().isEmpty(), "Question 4: Risks?");
        assertFalse(fallback.dataQualityCaveats().isEmpty(), "Question 3/5: Caveats");
        assertFalse(fallback.invalidationFactors().isEmpty(), "Question 5: What could invalidate this?");
        assertFalse(fallback.investigationQuestions().isEmpty(), "Question 6: What should investor investigate?");
    }

    @Test
    void testAiInterpretationServiceFallbackWhenNoApiKey() {
        SchemeOption option = createMockSchemeOption(10189L, "HDFC Flexi Cap Fund", "118955");
        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(option));

        GroundedAiInterpretationDto result = aiInterpretationService.getInterpretationForSchemeOption(10189L);

        assertNotNull(result);
        assertTrue(result.isFallback());
        assertEquals("DETERMINISTIC_FALLBACK", result.epistemicStatus());
    }

    @Test
    void testSafetyFilterRejectsNumericalClaimsNotPresentInEvidencePackage() {
        GroundedEvidencePackageDto evidence = new GroundedEvidencePackageDto(
            10189L, "HDFC Flexi Cap Fund", "118955", "INF179K01UT0", "Equity", "DIRECT", "GROWTH",
            7851L, "2024-01-15", "2024-01-31T23:59:59+05:30", 72.4, "SCORED",
            Map.of("Returns", 75.0),
            Map.of("RET-02", Map.of("code", "RET-02", "name", "Return", "value", 2.45)),
            Map.of(), "YUKIRA_SCORE_V1", List.of(), null
        );

        GroundedAiInterpretationDto ungroundedDto = new GroundedAiInterpretationDto(
            "Analysis of HDFC Flexi Cap Fund.",
            List.of("The fund overall score was 72.4."),
            List.of("The fund generated an ungrounded return of 48.9% in 2024."),
            List.of("Market risk applies."),
            List.of("Caveats."),
            List.of("Invalidation."),
            List.of("Questions."),
            Map.of(), 7851L, 10189L, "YUKIRA_SCORE_V1", "2024-01-15", "2024-01-31",
            List.of(), "VALIDATED_GROUNDED_AI", false, "test-model", "2026-10-06"
        );

        AiOutputValidationService.ValidationResult result = validationService.validateInterpretation(ungroundedDto, evidence);
        assertFalse(result.isValid());
        assertTrue(result.failureReason().contains("Ungrounded numerical claim"));
    }

    @Test
    void testSafetyFilterRejectsPunctuationAttachedForbiddenTerms() {
        GroundedAiInterpretationDto punctuationDto = new GroundedAiInterpretationDto(
            "Analysis summary.",
            List.of("Factual observation."),
            List.of("Investors should buy."),
            List.of("Risk factor."),
            List.of("Caveat."),
            List.of("Invalidation."),
            List.of("Question."),
            Map.of(), 100L, 10189L, "V1", "2024-01-15", "2024-01-31",
            List.of(), "VALIDATED_GROUNDED_AI", false, "test-model", "2026-10-06"
        );

        AiOutputValidationService.ValidationResult result = validationService.validateInterpretation(punctuationDto);
        assertFalse(result.isValid());
        assertTrue(result.failureReason().contains("buy"));
    }

    private SchemeOption createMockSchemeOption(Long id, String name, String amfi) {
        Scheme scheme = new Scheme();
        scheme.setId(100L);
        scheme.setName(name);
        scheme.setCategory("Equity");

        com.yukira.backend.domain.entity.SchemePlan plan = new com.yukira.backend.domain.entity.SchemePlan();
        plan.setId(200L);
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        SchemeOption option = new SchemeOption();
        option.setId(id);
        option.setPlan(plan);
        option.setAmfiCode(amfi);
        option.setIsin("INF179K01UT0");
        option.setOptionType("GROWTH");

        return option;
    }
}


