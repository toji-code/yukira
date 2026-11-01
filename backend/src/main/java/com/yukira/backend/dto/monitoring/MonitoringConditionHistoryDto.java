package com.yukira.backend.dto.monitoring;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record MonitoringConditionHistoryDto(
    Long id,
    Long conditionId,
    Long executionId,
    ZonedDateTime evaluatedAt,
    LocalDate observationCutoff,
    ZonedDateTime knowledgeCutoff,
    String evaluationOutcome,
    String triggerStatus,
    String withholdingReason,
    String evaluatedValue,
    String thresholdValue,
    String sourceContext,
    Long eventId
) {}

