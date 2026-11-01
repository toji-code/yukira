package com.yukira.backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.dto.ai.EvidenceClaimDto;
import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Main Grounded AI Interpretation Orchestration Service.
 *
 * Epistemic Contract:
 * - Coordinates deterministic evidence grounding, LLM provider integration, output safety validation, and fallback handling.
 * - Numerical computations remain strictly outside the AI path.
 * - Safely falls back to deterministic rule synthesis if LLM provider is unconfigured, times out, or fails safety audit.
 * - Generates structured claims, multi-horizon analysis, and specific due-diligence questions.
 */
@Service
public class AiInterpretationService {

    private static final Logger log = LoggerFactory.getLogger(AiInterpretationService.class);

    private final EvidenceGrounderService evidenceGrounder;
    private final DeterministicInterpretationFallbackEngine fallbackEngine;
    private final AiOutputValidationService validationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_CACHE_ENTRIES = 500;

    private final Map<String, GroundedAiInterpretationDto> interpretationCache = Collections.synchronizedMap(
        new LinkedHashMap<String, GroundedAiInterpretationDto>(MAX_CACHE_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, GroundedAiInterpretationDto> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        }
    );

    @Value("${yukira.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${yukira.ai.model:gemini-2.5-flash}")
    private String aiModel;

    @Value("${GEMINI_API_KEY:${YUKIRA_AI_API_KEY:}}")
    private String apiKey;

    public AiInterpretationService(
        EvidenceGrounderService evidenceGrounder,
        DeterministicInterpretationFallbackEngine fallbackEngine,
        AiOutputValidationService validationService
    ) {
        this.evidenceGrounder = evidenceGrounder;
        this.fallbackEngine = fallbackEngine;
        this.validationService = validationService;
    }

    public GroundedAiInterpretationDto getInterpretationForRun(Long runId) {
        GroundedEvidencePackageDto evidence = evidenceGrounder.buildEvidencePackageForRun(runId);
        String cacheKey = "run:" + runId + ":" + evidence.asOfDate() + ":" + (evidence.methodologyStatus() != null ? evidence.methodologyStatus() : "V1") + ":" + aiModel;
        if (interpretationCache.containsKey(cacheKey)) {
            return interpretationCache.get(cacheKey);
        }

        GroundedAiInterpretationDto result = produceInterpretation(evidence);
        interpretationCache.put(cacheKey, result);
        return result;
    }

    public GroundedAiInterpretationDto getInterpretationForSchemeOption(Long schemeOptionId) {
        GroundedEvidencePackageDto evidence = evidenceGrounder.buildEvidencePackageForSchemeOption(schemeOptionId);
        String scoreTag = evidence.overallScore() != null ? String.format(Locale.ROOT, "%.2f", evidence.overallScore()) : "no-score";
        String runTag = evidence.calculationRunId() != null ? String.valueOf(evidence.calculationRunId()) : "no-run";
        String cacheKey = "scheme:" + schemeOptionId + ":" + runTag + ":" + evidence.asOfDate() + ":" + scoreTag + ":" + aiModel;
        if (interpretationCache.containsKey(cacheKey)) {
            return interpretationCache.get(cacheKey);
        }

        GroundedAiInterpretationDto result = produceInterpretation(evidence);
        interpretationCache.put(cacheKey, result);
        return result;
    }

    /**
     * Retrieves grounded AI interpretation directly from an authoritative AnalyticalProfileResponse.
     * Prevents calculation redundancy and guarantees 100% synchronization with the profile on screen.
     */
    public GroundedAiInterpretationDto getInterpretationForProfile(AnalyticalProfileResponse profile) {
        if (profile == null) {
            throw new IllegalArgumentException("AnalyticalProfileResponse cannot be null for interpretation.");
        }
        GroundedEvidencePackageDto evidence = evidenceGrounder.buildEvidencePackageForProfile(profile);
        String horizon = evidence.horizon() != null ? evidence.horizon() : "3Y";
        String runTag = evidence.calculationRunId() != null ? String.valueOf(evidence.calculationRunId()) : "latest";
        String cacheKey = "profile:" + evidence.schemeOptionId() + ":" + runTag + ":" + horizon + ":" + evidence.asOfDate() + ":" + evidence.knowledgeCutoff() + ":" + aiModel;

        if (interpretationCache.containsKey(cacheKey)) {
            return interpretationCache.get(cacheKey);
        }

        GroundedAiInterpretationDto result = produceInterpretation(evidence);
        interpretationCache.put(cacheKey, result);
        return result;
    }

    /**
     * Evaluates and retrieves grounded AI interpretation for a scheme option under explicit horizon & date context.
     */
    public GroundedAiInterpretationDto getInterpretationForProfile(
        Long schemeOptionId,
        String horizon,
        String asOfDate,
        String knowledgeCutoffTime
    ) {
        AnalyticalHorizon effectiveHorizon = AnalyticalHorizon.fromCode(horizon);
        LocalDate effectiveAsOf = asOfDate != null && !asOfDate.isBlank() ? LocalDate.parse(asOfDate) : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoffTime != null && !knowledgeCutoffTime.isBlank()
            ? OffsetDateTime.parse(knowledgeCutoffTime)
            : OffsetDateTime.now();

        String cacheKey = "profile-query:" + schemeOptionId + ":" + effectiveHorizon.getCode() + ":" + effectiveAsOf + ":" + effectiveCutoff + ":" + aiModel;
        if (interpretationCache.containsKey(cacheKey)) {
            return interpretationCache.get(cacheKey);
        }

        GroundedEvidencePackageDto evidence = evidenceGrounder.buildEvidencePackageForProfile(
            schemeOptionId, effectiveHorizon, effectiveAsOf, effectiveCutoff
        );

        GroundedAiInterpretationDto result = produceInterpretation(evidence);
        interpretationCache.put(cacheKey, result);
        return result;
    }

    public GroundedAiInterpretationDto getInterpretationForPortfolio(String investorSubject) {
        GroundedEvidencePackageDto evidence = evidenceGrounder.buildEvidencePackageForPortfolio(investorSubject);
        String subjectTag = investorSubject != null ? investorSubject : "anonymous";
        String holdingCountTag = evidence.portfolioContext() != null && evidence.portfolioContext().containsKey("holdingCount")
            ? String.valueOf(evidence.portfolioContext().get("holdingCount")) : "0";
        String scoreTag = evidence.overallScore() != null ? String.format(Locale.ROOT, "%.2f", evidence.overallScore()) : "no-score";
        String cacheKey = "portfolio:" + subjectTag + ":" + evidence.asOfDate() + ":" + holdingCountTag + ":" + scoreTag + ":" + aiModel;
        if (interpretationCache.containsKey(cacheKey)) {
            return interpretationCache.get(cacheKey);
        }

        GroundedAiInterpretationDto result = produceInterpretation(evidence);
        interpretationCache.put(cacheKey, result);
        return result;
    }

    private GroundedAiInterpretationDto produceInterpretation(GroundedEvidencePackageDto evidence) {
        if (!aiEnabled || apiKey == null || apiKey.isBlank()) {
            log.info("AI provider disabled or API key unconfigured; using deterministic fallback engine.");
            return fallbackEngine.generateInterpretation(evidence);
        }

        try {
            GroundedAiInterpretationDto aiResult = callGeminiProvider(evidence);
            AiOutputValidationService.ValidationResult val = validationService.validateInterpretation(aiResult, evidence);
            if (val.isValid()) {
                return aiResult;
            } else {
                log.warn("AI output safety audit failed: {}. Falling back to deterministic engine.", val.failureReason());
                return fallbackEngine.generateInterpretation(evidence);
            }
        } catch (Exception e) {
            log.warn("AI provider execution failed ({}). Falling back to deterministic engine.", e.getMessage());
            return fallbackEngine.generateInterpretation(evidence);
        }
    }

    private GroundedAiInterpretationDto callGeminiProvider(GroundedEvidencePackageDto evidence) throws Exception {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", aiModel, apiKey);

        String systemPrompt = """
            You are YUKIRA's Grounded Quantitative Interpretation Layer for Indian mutual fund allocators.
            Your job is strictly to explain the provided quantitative evidence in structured JSON format.

            RULES:
            1. NEVER calculate or modify numbers. Use ONLY the provided evidence package.
            2. NEVER issue investment advice or BUY, SELL, HOLD, REDUCE, AVOID recommendations.
            3. NEVER predict future price or returns.
            4. Structure your response into strictly valid JSON matching this schema:
               {
                 "summary": "Concise executive interpretation of the fund over the selected horizon",
                 "whatHappened": ["Factual observation 1", "Factual observation 2"],
                 "returnCharacteristics": ["Return explanation 1", "Return explanation 2"],
                 "riskCharacteristics": ["Risk analysis 1", "Risk analysis 2"],
                 "horizonInterpretation": ["Horizon observation 1"],
                 "interpretation": ["Analytical meaning 1", "Analytical meaning 2"],
                 "riskFactors": ["Key risk factor 1", "Key risk factor 2"],
                 "dataQualityCaveats": ["Data quality or governance limitation 1"],
                 "invalidationFactors": ["What could invalidate this interpretation 1"],
                 "investigationQuestions": ["Specific prioritized due-diligence question 1", "Question 2"],
                 "claims": [
                   {
                     "claimText": "Claim description",
                     "claimType": "OBSERVATION",
                     "supportingMetricCodes": ["RET-03"],
                     "horizon": "3Y",
                     "methodologyStatus": "APPROVED_BASELINE"
                   }
                 ]
               }
            """;

        String evidenceJson = objectMapper.writeValueAsString(evidence);
        String prompt = systemPrompt + "\n\nEVIDENCE PACKAGE JSON:\n" + evidenceJson;

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            ),
            "generationConfig", Map.of(
                "temperature", 0.2,
                "responseMimeType", "application/json"
            )
        );

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        RestTemplate restTemplate = new RestTemplate(factory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("HTTP " + response.getStatusCode() + " from Gemini API");
        }

        Map<?, ?> respMap = objectMapper.readValue(response.getBody(), Map.class);
        List<?> candidates = (List<?>) respMap.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Empty candidates array in Gemini API response");
        }

        Map<?, ?> firstCand = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) firstCand.get("content");
        List<?> parts = (List<?>) content.get("parts");
        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
        String rawJson = (String) firstPart.get("text");

        Map<?, ?> jsonMap = objectMapper.readValue(rawJson, Map.class);

        List<String> whatHappened = castToStringList(jsonMap.get("whatHappened"));
        List<String> interpretation = castToStringList(jsonMap.get("interpretation"));
        List<String> returnCharacteristics = castToStringList(jsonMap.get("returnCharacteristics"));
        List<String> riskCharacteristics = castToStringList(jsonMap.get("riskCharacteristics"));
        List<String> horizonInterpretation = castToStringList(jsonMap.get("horizonInterpretation"));
        List<String> riskFactors = castToStringList(jsonMap.get("riskFactors"));
        List<String> dataQualityCaveats = castToStringList(jsonMap.get("dataQualityCaveats"));
        List<String> invalidationFactors = castToStringList(jsonMap.get("invalidationFactors"));
        List<String> investigationQuestions = castToStringList(jsonMap.get("investigationQuestions"));
        String summary = (String) jsonMap.get("summary");

        List<EvidenceClaimDto> claims = new ArrayList<>();
        if (jsonMap.get("claims") instanceof List<?> claimList) {
            for (Object item : claimList) {
                if (item instanceof Map<?, ?> cm) {
                    String ct = (String) cm.get("claimText");
                    String type = (String) cm.get("claimType");
                    List<String> codes = castToStringList(cm.get("supportingMetricCodes"));
                    String h = (String) cm.get("horizon");
                    String ms = (String) cm.get("methodologyStatus");
                    if (ct != null && !ct.isBlank()) {
                        claims.add(new EvidenceClaimDto(ct, type != null ? type : "OBSERVATION", codes, h != null ? h : evidence.horizon(), ms != null ? ms : evidence.methodologyStatus()));
                    }
                }
            }
        }

        Map<String, Object> evidenceRefs = new LinkedHashMap<>();
        if (evidence.overallScore() != null) evidenceRefs.put("overallScore", evidence.overallScore());
        if (evidence.metricValues() != null) evidenceRefs.putAll(evidence.metricValues());

        return new GroundedAiInterpretationDto(
            summary != null ? summary : "Grounded AI Quantitative Summary",
            whatHappened,
            interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceRefs,
            evidence.calculationRunId(),
            evidence.schemeOptionId(),
            evidence.methodologyStatus() != null ? evidence.methodologyStatus() : "YUKIRA_PROFILE_V1",
            evidence.asOfDate(),
            evidence.knowledgeCutoff(),
            evidence.sourceArtifactDigests() != null ? evidence.sourceArtifactDigests() : Collections.emptyList(),
            "VALIDATED_GROUNDED_AI",
            false,
            aiModel,
            Instant.now().toString(),
            evidence.horizon() != null ? evidence.horizon() : "3Y",
            claims,
            returnCharacteristics,
            riskCharacteristics,
            horizonInterpretation
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> castToStringList(Object obj) {
        if (obj instanceof List<?> l) {
            List<String> res = new ArrayList<>();
            for (Object o : l) {
                if (o != null) res.add(o.toString());
            }
            return res;
        }
        return Collections.emptyList();
    }
}
