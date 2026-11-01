package com.yukira.backend.dto.monitoring;

import java.time.ZonedDateTime;
import java.util.List;

public record MonitoringEvaluationResponseDto(
    int evaluatedFundsCount,
    int evaluatedPortfolioPositionsCount,
    int evaluatedConditionsCount,
    int newEventsDetectedCount,
    int totalActiveEventsCount,
    ZonedDateTime evaluationTimestamp,
    List<String> evaluationSummaryNotes
) {}

