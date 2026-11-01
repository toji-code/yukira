package com.yukira.backend.dto.monitoring;

import java.math.BigDecimal;

/**
 * Direct Finding-to-Monitoring Handoff Request (Task 89).
 * Allows one-click conversion of an active stress finding into a continuously tracked monitoring condition.
 */
public record CreateConditionFromFindingRequest(
    String findingId,
    Long portfolioId,
    Long schemeOptionId,
    String conditionName,
    String evidenceField,
    String operator,
    String thresholdValue,
    BigDecimal thresholdNumeric,
    String thresholdUnit,
    String timeWindow,
    String thesisReconsiderationGuidance
) {}

