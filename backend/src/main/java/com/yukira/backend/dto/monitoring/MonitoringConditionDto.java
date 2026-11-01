package com.yukira.backend.dto.monitoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record MonitoringConditionDto(
    Long id,
    Long investorId,
    Long schemeId,
    Long schemeOptionId,
    String schemeCode,
    String schemeName,
    String targetScope,
    String conditionName,
    String evidenceField,
    String operator,
    String thresholdValue,
    BigDecimal thresholdNumeric,
    String thresholdUnit,
    String thresholdClassification,
    String timeWindow,
    String associatedThesisKey,
    String thesisReconsiderationGuidance,
    Boolean isEnabled,
    String evaluationState,
    String evidenceState,
    String lastEvaluatedValue,
    ZonedDateTime lastEvaluatedAt,
    LocalDate lastObservationDate,
    Integer triggerCount,
    ZonedDateTime lastTriggeredAt,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    Long portfolioId,
    String stressFindingId,
    String conditionStatus,
    String originatingFindingId
) {
    public MonitoringConditionDto(
        Long id,
        Long investorId,
        Long schemeId,
        Long schemeOptionId,
        String schemeCode,
        String schemeName,
        String targetScope,
        String conditionName,
        String evidenceField,
        String operator,
        String thresholdValue,
        BigDecimal thresholdNumeric,
        String thresholdUnit,
        String thresholdClassification,
        String timeWindow,
        String associatedThesisKey,
        String thesisReconsiderationGuidance,
        Boolean isEnabled,
        String evaluationState,
        String evidenceState,
        String lastEvaluatedValue,
        ZonedDateTime lastEvaluatedAt,
        LocalDate lastObservationDate,
        Integer triggerCount,
        ZonedDateTime lastTriggeredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        this(
            id, investorId, schemeId, schemeOptionId, schemeCode, schemeName, targetScope,
            conditionName, evidenceField, operator, thresholdValue, thresholdNumeric,
            thresholdUnit, thresholdClassification, timeWindow, associatedThesisKey,
            thesisReconsiderationGuidance, isEnabled, evaluationState, evidenceState,
            lastEvaluatedValue, lastEvaluatedAt, lastObservationDate, triggerCount,
            lastTriggeredAt, createdAt, updatedAt, null, null, "ACTIVE", null
        );
    }

    public MonitoringConditionDto(
        Long id,
        Long investorId,
        Long schemeId,
        Long schemeOptionId,
        String schemeCode,
        String schemeName,
        String targetScope,
        String conditionName,
        String evidenceField,
        String operator,
        String thresholdValue,
        BigDecimal thresholdNumeric,
        String thresholdUnit,
        String thresholdClassification,
        String timeWindow,
        String associatedThesisKey,
        String thesisReconsiderationGuidance,
        Boolean isEnabled,
        String evaluationState,
        String evidenceState,
        String lastEvaluatedValue,
        ZonedDateTime lastEvaluatedAt,
        LocalDate lastObservationDate,
        Integer triggerCount,
        ZonedDateTime lastTriggeredAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        Long portfolioId,
        String stressFindingId
    ) {
        this(
            id, investorId, schemeId, schemeOptionId, schemeCode, schemeName, targetScope,
            conditionName, evidenceField, operator, thresholdValue, thresholdNumeric,
            thresholdUnit, thresholdClassification, timeWindow, associatedThesisKey,
            thesisReconsiderationGuidance, isEnabled, evaluationState, evidenceState,
            lastEvaluatedValue, lastEvaluatedAt, lastObservationDate, triggerCount,
            lastTriggeredAt, createdAt, updatedAt, portfolioId, stressFindingId, "ACTIVE", stressFindingId
        );
    }
}
