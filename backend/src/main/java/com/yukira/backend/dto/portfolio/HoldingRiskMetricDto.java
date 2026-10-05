package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record HoldingRiskMetricDto(
    String metricCode,
    String metricName,
    BigDecimal numericValue,
    String formattedValue,
    String unit,
    String asOfDate,
    Integer observationCount,
    String calculationStatus,
    String availabilityState
) {}
