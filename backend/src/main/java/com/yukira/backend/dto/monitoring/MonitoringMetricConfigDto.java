package com.yukira.backend.dto.monitoring;

import java.math.BigDecimal;
import java.util.List;

/**
 * Metadata definition for metrics and evidence fields supported by the continuous monitoring engine.
 * Aligns monitoring options directly with AuthoritativeMetricRegistryService.
 */
public record MonitoringMetricConfigDto(
    String metricCode,
    String metricName,
    String category,
    String unit,
    Double scaleFactor,
    Boolean isNegativeConvention,
    String defaultOperator,
    List<String> supportedOperators,
    BigDecimal candidateThresholdNumeric,
    String candidateThresholdDisplay,
    String guidance,
    String governanceStatus
) {}

