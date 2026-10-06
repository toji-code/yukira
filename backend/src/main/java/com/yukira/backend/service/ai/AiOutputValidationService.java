package com.yukira.backend.service.ai;

import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Output Safety & Verification Filter.
 *
 * Epistemic Contract:
 * - Treats all LLM output as untrusted external text.
 * - Enforces strict rejection of investment advice, return forecasts, buy/sell/hold recommendations, and unevidenced claims.
 * - Validates numerical claims in prose strictly against supplied GroundedEvidencePackageDto.
 * - If output violates safety rules or contract completeness, triggers deterministic fallback.
 */
@Service
public class AiOutputValidationService {

    private static final Logger log = LoggerFactory.getLogger(AiOutputValidationService.class);

    private static final List<String> FORBIDDEN_ADVISORY_TERMS = List.of(
        "buy", "sell", "hold", "reduce", "avoid",
        "strong buy", "strong sell", "must buy", "must sell",
        "guarantee", "guaranteed", "will outperform", "future return", "future returns",
        "expected return", "price target", "target price", "rating upgrade",
        "recommend", "recommendation", "recommended"
    );

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
        "\\b(" + String.join("|", FORBIDDEN_ADVISORY_TERMS) + ")\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?%?\\b");

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])\\b");

    public record ValidationResult(boolean isValid, String failureReason) {}

    public ValidationResult validateInterpretation(GroundedAiInterpretationDto dto) {
        return validateInterpretation(dto, null);
    }

    public ValidationResult validateInterpretation(GroundedAiInterpretationDto dto, GroundedEvidencePackageDto evidence) {
        if (dto == null) {
            return new ValidationResult(false, "Interpretation DTO is null.");
        }

        // Structural completeness checks
        if (dto.summary() == null || dto.summary().isBlank()) {
            return new ValidationResult(false, "Summary field is missing or empty.");
        }
        if (dto.whatHappened() == null || dto.whatHappened().isEmpty()) {
            return new ValidationResult(false, "whatHappened list is empty.");
        }
        if (dto.interpretation() == null || dto.interpretation().isEmpty()) {
            return new ValidationResult(false, "interpretation list is empty.");
        }
        if (dto.riskFactors() == null || dto.riskFactors().isEmpty()) {
            return new ValidationResult(false, "riskFactors list is empty.");
        }

        // Combine prose for content safety verification
        StringBuilder prose = new StringBuilder();
        prose.append(dto.summary()).append(" ");
        dto.whatHappened().forEach(s -> prose.append(s).append(" "));
        dto.interpretation().forEach(s -> prose.append(s).append(" "));
        dto.riskFactors().forEach(s -> prose.append(s).append(" "));
        if (dto.dataQualityCaveats() != null) dto.dataQualityCaveats().forEach(s -> prose.append(s).append(" "));
        if (dto.invalidationFactors() != null) dto.invalidationFactors().forEach(s -> prose.append(s).append(" "));
        if (dto.investigationQuestions() != null) dto.investigationQuestions().forEach(s -> prose.append(s).append(" "));

        String fullText = prose.toString();

        Matcher forbiddenMatcher = FORBIDDEN_PATTERN.matcher(fullText);
        if (forbiddenMatcher.find()) {
            String matched = forbiddenMatcher.group();
            log.warn("AI output validation failed: contains forbidden advisory term '{}'", matched);
            return new ValidationResult(false, "Output contained forbidden advisory term: '" + matched + "'");
        }

        // Numerical Grounding Check if evidence package is provided
        if (evidence != null) {
            ValidationResult numResult = validateNumericalGrounding(fullText, evidence);
            if (!numResult.isValid()) {
                return numResult;
            }
        }

        return new ValidationResult(true, "OK");
    }

    private ValidationResult validateNumericalGrounding(String text, GroundedEvidencePackageDto evidence) {
        Set<Double> allowedValues = extractAllowedNumbers(evidence);

        // Strip dates first to avoid treating 2024, 01, 15 as financial numbers
        String textNoDates = DATE_PATTERN.matcher(text).replaceAll(" ");

        Matcher numberMatcher = NUMBER_PATTERN.matcher(textNoDates);
        while (numberMatcher.find()) {
            String token = numberMatcher.group().replace("%", "");
            try {
                double val = Double.parseDouble(token);

                // Ignore standard structural numbers (1-5 bullet/question indices or common small counts under 10 unless negative)
                if (val >= 1.0 && val <= 5.0 && !textNoDates.contains(token + ".")) {
                    // Check if bullet index or valid count
                }

                // Skip standalone years if any remain (e.g. 2024, 2025, 2026)
                if (val >= 2020.0 && val <= 2030.0) {
                    continue;
                }

                // Verify against allowed evidence numbers
                boolean matched = false;
                for (Double allowed : allowedValues) {
                    if (allowed == null) continue;
                    if (Math.abs(val - allowed) <= 0.05 || (allowed != 0 && Math.abs((val - allowed) / allowed) <= 0.05)) {
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    log.warn("AI output validation failed: unevidenced numerical claim '{}' not found in evidence package.", val);
                    return new ValidationResult(false, "Ungrounded numerical claim in LLM prose: " + val);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return new ValidationResult(true, "OK");
    }

    private Set<Double> extractAllowedNumbers(GroundedEvidencePackageDto evidence) {
        Set<Double> numbers = new HashSet<>();

        if (evidence.overallScore() != null) {
            numbers.add(evidence.overallScore());
            numbers.add(evidence.overallScore() / 100.0);
        }

        if (evidence.schemeOptionId() != null) {
            numbers.add(evidence.schemeOptionId().doubleValue());
        }

        if (evidence.calculationRunId() != null) {
            numbers.add(evidence.calculationRunId().doubleValue());
        }

        if (evidence.dimensionScores() != null) {
            for (Double d : evidence.dimensionScores().values()) {
                if (d != null) {
                    numbers.add(d);
                    numbers.add(d / 100.0);
                }
            }
        }

        if (evidence.metricValues() != null) {
            for (Object obj : evidence.metricValues().values()) {
                if (obj instanceof Map<?, ?> m) {
                    Object val = m.get("value");
                    if (val instanceof Number n) {
                        double v = n.doubleValue();
                        numbers.add(v);
                        numbers.add(v * 100.0);
                        numbers.add(v / 100.0);
                    }
                    Object obs = m.get("observationCount");
                    if (obs instanceof Number n) {
                        numbers.add(n.doubleValue());
                    }
                }
            }
        }

        if (evidence.portfolioContext() != null) {
            for (Object obj : evidence.portfolioContext().values()) {
                if (obj instanceof Number n) {
                    double v = n.doubleValue();
                    numbers.add(v);
                    numbers.add(v * 100.0);
                    numbers.add(v / 100.0);
                }
            }
        }

        return numbers;
    }
}

