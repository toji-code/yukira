package com.yukira.backend.dto.monitoring;

import java.math.BigDecimal;

public record MonitoringConditionRequest(
    Long schemeId,
    Long schemeOptionId,
    String targetScope, // SCHEME_OPTION or PORTFOLIO
    String conditionName,
    String evidenceField,
    String operator, // GT, GTE, LT, LTE, ABS_CHANGE_GT, DATA_UNAVAILABLE, EVIDENCE_STALE
    String thresholdValue,
    BigDecimal thresholdNumeric,
    String thresholdUnit,
    String thresholdClassification,
    String timeWindow,
    String associatedThesisKey,
    String thesisReconsiderationGuidance,
    Boolean isEnabled,
    Long portfolioId,
    String stressFindingId
) {
    public MonitoringConditionRequest(
        Long schemeId,
        Long schemeOptionId,
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
        Boolean isEnabled
    ) {
        this(
            schemeId, schemeOptionId, targetScope, conditionName, evidenceField, operator,
            thresholdValue, thresholdNumeric, thresholdUnit, thresholdClassification,
            timeWindow, associatedThesisKey, thesisReconsiderationGuidance, isEnabled,
            null, null
        );
    }
}
