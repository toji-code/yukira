package com.yukira.backend.dto.monitoring;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Structured AI-grounded or deterministic fallback explanation for a monitoring event.
 *
 * Epistemic Contract:
 * - AI explains observed facts and surfaces investigation questions.
 * - ZERO advice: Never generates BUY, SELL, HOLD recommendations.
 * - Distinguishes between verified evidence, interpretations, and data artifacts.
 */
public record MonitoringAiExplanationDto(
    Long eventId,
    String eventClassification,
    String metricName,
    String plainLanguageExplanation,
    String historicalEvidenceSummary,
    List<String> possibleImplications,
    List<String> conflictingEvidenceOrCaveats,
    String missingInformationOrLimitations,
    List<String> specificInvestigationQuestions,
    boolean isAiGenerated,
    String providerModel,
    ZonedDateTime generatedAt,
    String governanceDisclaimer
) {}

