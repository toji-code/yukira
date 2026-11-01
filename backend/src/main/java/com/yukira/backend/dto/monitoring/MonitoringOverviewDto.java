package com.yukira.backend.dto.monitoring;

import java.time.ZonedDateTime;

/**
 * High-level overview of active monitoring status for an investor.
 *
 * Epistemic Contract:
 * - Event counts reflect factual evidence changes and threshold evaluations.
 * - ZERO investment scoring: Event counts are NEVER combined into a composite fund or portfolio rating.
 */
public record MonitoringOverviewDto(
    long totalActiveEvents,
    long highSeverityCount,
    long mediumSeverityCount,
    long lowSeverityCount,
    long infoSeverityCount,
    long awaitingAcknowledgementCount,
    long investigatingCount,
    long resolvedCount,
    long dismissedCount,
    long triggeredThesisConditionsCount,
    long dataFreshnessWarningsCount,
    long monitoredFundsCount,
    long monitoredPortfolioPositionsCount,
    ZonedDateTime lastMonitoringEvaluationTime,
    String latestExecutionStatus,
    ZonedDateTime nextScheduledEvaluation,
    long unavailableEvidenceCount,
    long staleSourceCount,
    String epistemicDisclaimer
) {}
